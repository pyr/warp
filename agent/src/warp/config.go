package warp

import (
	"io/ioutil"
	"encoding/json"
	"fmt"
	"os"
)

type Config struct {
	Host string  `json:"host" binding:"required"`
	Cert string `json:"cert" binding:"required"`
	CaCert string `json:"cacert" binding:"required"`
	PrivKey string `json:"privkey" binding:"required"`
	Server string `json:"server" binding:"required"`
	LogTo string `json:"log" binding:"required"`
}

func ReadConfig(path string) Config {

	data, err := ioutil.ReadFile(path)
	if err != nil {
		fmt.Printf("cannot read configuration: %v", err)
		os.Exit(1)
	}

	var cfg Config
	json.Unmarshal(data, &cfg)

	return cfg
}
