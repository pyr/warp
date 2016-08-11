package warp

import (
	"bytes"
	"encoding/json"
	"fmt"
	"log"
	"os/exec"
	"strings"
	"sync"
	"time"
)

type Environment interface {
	Host() string
	Lookup(string) string
}

type MapEnvironment struct {
	internal map[string]string
	lock     sync.RWMutex
}

func (me MapEnvironment) Host() string {
	me.lock.RLock()
	defer me.lock.RUnlock()
	return me.internal["host"]
}

func (me MapEnvironment) Lookup(k string) string {
	me.lock.RLock()
	defer me.lock.RUnlock()
	return me.internal[k]
}

func NewEnvironment(host string) *MapEnvironment {
	return &MapEnvironment{
		internal: map[string]string{
			"host": host,
		},
	}
}

func updatePayload(payload map[string]string) {
	for {
		time.Sleep(10 * time.Second)
	}
}

func PrefixedKey(prefix string, k string) string {
	if prefix == "" {
		return k
	} else {
		return fmt.Sprintf("%s.%s", prefix, k)
	}
}

func BuildPrefixedEnv(env map[string]string, prefix string, values map[string]interface{}) {

	for k, v := range values {
		pk := PrefixedKey(prefix, k)
		switch x := v.(type) {
		case map[string]interface{}:
			BuildPrefixedEnv(env, fmt.Sprintf(pk), x)
		default:
			env[pk] = fmt.Sprintf("%v", x)
		}
	}
}

func BuildEnv(logger *log.Logger, host string, env *MapEnvironment) {

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

		env.lock.Lock()
		for k := range env.internal {
			delete(env.internal, k)
		}
		env.internal["host"] = host
		BuildPrefixedEnv(env.internal, "facter", outmap)
		env.lock.Unlock()
		time.Sleep(120 * time.Second)
	}
}
