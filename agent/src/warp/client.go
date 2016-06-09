package warp

import (
	"fmt"
	"errors"
	"bytes"
	"os"
	"time"
	"log"
	"log/syslog"
	"crypto/tls"
	"crypto/x509"
	"io/ioutil"
	"sync"
	"encoding/binary"
	"encoding/json"
)

type Client struct {
	Config Config
	Conn *tls.Conn
	Logger *log.Logger
	Connected bool
	Input chan *Packet
	Output chan *Packet
}

func NewClient(cfg Config) *Client {

	var logger *log.Logger
	var err error

	switch {
	case cfg.LogTo == "stdout":
		logger = log.New(os.Stdout, "warp: ", 0)
	case cfg.LogTo == "stderr":
		logger = log.New(os.Stderr, "warp: ", 0)
	case cfg.LogTo == "syslog":
		logger, err = syslog.NewLogger(syslog.LOG_INFO | syslog.LOG_DAEMON, 0)
		if err != nil {
			fmt.Printf("cannot initialize logger: %v", err)
			os.Exit(1)
		}
	default:
		fmt.Printf("cannot initialize logger, bad configuration")
		os.Exit(1)
	}

	cert, err := tls.LoadX509KeyPair(cfg.Cert, cfg.PrivKey)
	if err != nil {
		logger.Fatalf("cannot load certificate pair: %v", err)
	}
	data, err := ioutil.ReadFile(cfg.CaCert)
	if err != nil {
		logger.Fatalf("cannot load cacertificate: %v", err)
	}
	capool := x509.NewCertPool()
	capool.AppendCertsFromPEM(data)

	tlscfg := &tls.Config{
		Certificates: []tls.Certificate{cert},
		RootCAs: capool,
	}
	tlscfg.BuildNameToCertificate()

	input := make(chan *Packet, 100)
	output := make(chan *Packet, 100)
	envmap := make(map[string]string)
	env := MapEnvironment{Internal: envmap}

	client := &Client{Config: cfg, Conn: nil, Logger: logger, Connected: false, Input: input, Output: output}
	envmap["host"] = cfg.Host

	go BuildEnv(logger, cfg.Host, envmap)

	go func() {
		for {
			if (client.Connected == false) {
				conn, err := tls.Dial("tcp", cfg.Server, tlscfg)
				if err != nil {
					logger.Printf("unabled to connect, will retry in 5 seconds: %v", err)
					time.Sleep(5 * time.Second)
					continue
				}
				client.Conn = conn
				client.Connected = true
				logger.Printf("connected")
			}
			p, err := ReadPacket(logger, client.Conn)
			if err != nil {
				logger.Printf("read error: %v", err)
				client.Connected = false
				client.Conn = nil
			} else {
				input <- p
			}
		}
	}()

	go func() {
		for {
			p := <- input
			client.HandleRequest(p, env)
		}
	}()

	go func() {
		for {
			p := <- output
			p.Host = cfg.Host
			if (client.Connected == false) {
				logger.Printf("not connected, dropping output packet")
				continue
			}
			err = WritePacket(logger, client.Conn, p)
			if err != nil {
				logger.Printf("failed to send packet")
			}
		}
	}()

	return client
}

func ReadPacket(logger *log.Logger, conn *tls.Conn) (*Packet, error) {

	inbuf := make([]byte, 1024)
	br, err := conn.Read(inbuf)
	if err != nil {
		return nil, err
	}
	if br == 0 {
		return nil, errors.New("disconnected")
	}
	if br < 4 {
		return nil, errors.New("short read")
	}

	br = br -4
	phead := inbuf[0:4]
	b := bytes.NewReader(phead)

	var pilen uint32
	err = binary.Read(b, binary.BigEndian, &pilen)
	plen := int(pilen) - 4
	if err != nil {
		conn.Close()
		return nil, err
	}

	pdata := inbuf[4:]
	for {
		if br >= plen {
			break
		}
		buf := make([]byte,(plen - br))
		nr, err := conn.Read(buf)
		if err != nil {
			conn.Close()
			return nil, err
		}
		if nr == 0 {
			return nil, err
		}
		pdata = append(pdata, buf...)
		br = br + nr
	}

	var p Packet
	err = json.Unmarshal(pdata[:plen], &p)

	if err != nil {
		return nil, err

	}
	return &p, nil
}

func WritePacket(logger *log.Logger, conn *tls.Conn, p *Packet) error {

	jsbuf, err := json.Marshal(&p)
	if err != nil {
		return err
	}

	plen := len(jsbuf)
	pilen := uint32(plen)

	outlen := new(bytes.Buffer)
	binary.Write(outlen, binary.BigEndian, &pilen)

	jsbuf = append(outlen.Bytes(), jsbuf...)

	bw := 0
	for {
		if bw >= (plen + 4) {
			break
		}
		w, err := conn.Write(jsbuf)
		if err != nil {
			return err
		}
		bw = bw + w
	}
	return nil
}

func (client *Client) SendPacket(p Packet) {
	client.Output <- &p
}

func (client *Client) WaitForQuit() {
	var wg sync.WaitGroup
	wg.Add(1)
	wg.Wait()
}
