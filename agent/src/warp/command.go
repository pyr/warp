package warp

import (
	"fmt"
	"strings"
	"bytes"
	"time"
	"os"
	"os/exec"
	"syscall"
)

type CommandType string

const (
	COMMAND_PING CommandType = "ping"
	COMMAND_SLEEP CommandType = "sleep"
	COMMAND_SERVICE CommandType = "service"
	COMMAND_SHELL CommandType = "shell"
)

type CommandDescription struct {
	Type CommandType `json:"type" binding:"required"`
	Seconds time.Duration `json:"seconds,omitempty"`
	Service *string `json:"service,omitempty"`
	Action *string `json:"action,omitempty"`
	Cwd *string `json:"cwd,omitempty"`
	Exits *[]int `json:"exits,omitempty"`
	ShellScript string `json:"shell,omitempty"`
}

type CommandOutput struct {
	Success bool `json:"success"`
	ExitCode int `json:"exit"`
	Output string `json:"output"`
}

type Command interface {
	Execute() CommandOutput
}

type PingCommand struct {
}

type SleepCommand struct {
	Seconds time.Duration
}

type ServiceCommand struct {
	Service string
	Action string
}

type ShellCommand struct {
	Cwd string
	Exits *[]int
	ShellScript string
}

func (pc PingCommand) Execute() CommandOutput {
	return CommandOutput{Success: true, ExitCode: 0, Output: "Alive, thanks."}
}

func (sc SleepCommand) Execute() CommandOutput {
	time.Sleep(sc.Seconds * time.Second)
	return CommandOutput{Success: true, ExitCode: 0, Output: "Yawn."}
}

func ValidExit(exits *[]int, exitCode int) bool {
	for _, e := range(*exits) {
		if e == exitCode {
			return true
		}
	}
	return false
}

func (sh ShellCommand) Execute() CommandOutput {
	oldwd, err := os.Getwd()
	if err != nil {
		return CommandOutput{
			Success: false,
			ExitCode: -1,
			Output: fmt.Sprintf("cannot get dir: %v", err),
		}
	}
	stdin := strings.NewReader("")
	var out bytes.Buffer

	if err = os.Chdir(sh.Cwd); err != nil {
		return CommandOutput{
			Success: false,
			ExitCode: -1,
			Output: fmt.Sprintf("cannot change dir: %v", err),
		}
	}
	cmd := exec.Command("bash", "-c", sh.ShellScript)
	cmd.Stdin = stdin
	cmd.Stdout = &out
	cmd.Stderr = &out
	err = cmd.Run()
	exitCode := 0
	output := ""
	if err != nil {
		if exitError, ok := err.(*exec.ExitError); ok {
			waitStatus := exitError.Sys().(syscall.WaitStatus)
			exitCode = waitStatus.ExitStatus()
			output = out.String()
		} else {
			exitCode = -1
			output  = fmt.Sprintf("cannot run process: %v", err)
		}
	} else {
		output = out.String()

	}
	os.Chdir(oldwd)
	return CommandOutput{Success: ValidExit(sh.Exits, exitCode), ExitCode: exitCode, Output: output}
}

func (sc ServiceCommand) Execute() CommandOutput {
	stdin := strings.NewReader("")

	var out bytes.Buffer

	cmd := exec.Command("service", sc.Service, sc.Action)
	cmd.Stdin = stdin
	cmd.Stdout = &out
	cmd.Stderr = &out

	err := cmd.Run()

	exitCode := 0
	output := ""
	if err != nil {
		if exitError, ok := err.(*exec.ExitError); ok {
			waitStatus := exitError.Sys().(syscall.WaitStatus)
			exitCode = waitStatus.ExitStatus()
			output = out.String()
		} else {
			exitCode = -1
			output  = fmt.Sprintf("cannot run process: %v", err)
		}
	} else {
		output = out.String()
	}
	return CommandOutput{Success: (exitCode == 0), ExitCode: exitCode, Output: output}
}

func (cd CommandDescription) DescriptionToCommand() Command {
	switch {
	case cd.Type == "ping":
		return PingCommand{}
	case cd.Type == "sleep":
		return SleepCommand{Seconds: cd.Seconds}
	case cd.Type == "service":
		return ServiceCommand{Service: *cd.Service, Action: *cd.Action}
	case cd.Type == "shell":
		cwd := "/"
		exits := &[]int{0}
		if cd.Cwd != nil {
			cwd = *cd.Cwd
		}
		if cd.Exits != nil {
			exits = cd.Exits
		}
		return ShellCommand{Cwd: cwd, Exits: exits, ShellScript: cd.ShellScript}
	}
	return PingCommand{}
}
