package warp

import (
	"time"
	"log"
	"bytes"
	"strings"
	"fmt"
	"encoding/json"
	"os/exec"
)

type Environment interface {
	Host() string
	Lookup(string) string
}

type MapEnvironment struct {
	Internal map[string]string
}

func (me MapEnvironment) Host() string {
	return me.Internal["host"]
}

func (me MapEnvironment) Lookup(k string) string {
	return me.Internal[k]
}

func updatePayload(payload map[string]string) {
	for {
		time.Sleep(10 * time.Second)
	}
}

func PrefixedKey(prefix string, k string) string {
	if (prefix == "") {
		return k
	} else {
		return fmt.Sprintf("%s.%s", prefix, k)
	}
}

func BuildPrefixedEnv(env map[string]string, prefix string, values map[string]interface{}) {


	for k,v := range(values) {
		pk := PrefixedKey(prefix, k)
		switch x := v.(type) {
		case map[string]interface{}:
			BuildPrefixedEnv(env, fmt.Sprintf(pk), x)
		default:
			env[pk] = fmt.Sprintf("%v", x)
		}
	}
}

func BuildEnv(logger *log.Logger, host string, env map[string]string) {

	for {
		stdin := strings.NewReader("")
		var out bytes.Buffer
		var errout bytes.Buffer
		cmd := exec.Command("facter", "-j", "--external-dir", "/etc/facter/facts.d")
		cmd.Stdin = stdin
		cmd.Stdout = &out
		cmd.Stderr = &errout
		outmap := make(map[string]interface{})

		err := cmd.Run()
		if err != nil {
			log.Printf("could not refresh environment: %v", err)
			return
		}

		err = json.Unmarshal(out.Bytes(), &outmap)
		if err != nil {
			log.Printf("could not refresh environment: %v", err)
			return
		}

		for k := range(env) {
			delete(env, k)
		}
		env["host"] = host
		BuildPrefixedEnv(env, "facter", outmap)
		time.Sleep(120 * time.Second)
	}
}
