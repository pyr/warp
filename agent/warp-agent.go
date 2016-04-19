package main

import (
	"os"
	"fmt"
	"warp"
)

func main() {
	if (len(os.Args) < 2) {
		fmt.Printf("not enough args, bye.\n")
		os.Exit(1)
	}

	cfg := warp.ReadConfig(os.Args[1])
	client := warp.NewClient(cfg)
	client.WaitForQuit()
	client.Logger.Printf("client has quit, bye.")
	os.Exit(0)
}
