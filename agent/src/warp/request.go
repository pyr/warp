package warp

type PacketOpcode string

const (
	OPCODE_PING          PacketOpcode = "ping"
	OPCODE_SCRIPT        PacketOpcode = "script"
	OPCODE_COMMAND_DENY  PacketOpcode = "command-denied"
	OPCODE_COMMAND_START PacketOpcode = "command-start"
	OPCODE_COMMAND_END   PacketOpcode = "command-end"
	OPCODE_COMMAND_STEP  PacketOpcode = "command-step"
	OPCODE_TOPOLOGY      PacketOpcode = "topology"
)

type PacketStatus string

type Scenario struct {
	Name     string               `json:"name" binding:"required"`
	Timeout  int                  `json:"timeout,omitempty"`
	Matcher  MatcherDescription   `json:"matcher" binding:"required"`
	Commands []CommandDescription `json:"commands" binding:"required"`
}

type Packet struct {
	Opcode     PacketOpcode      `json:"opcode" binding:"required"`
	Sequence   string            `json:"sequence" binding:"required"`
	Message    string            `json:"message,omitempty"`
	Topology   map[string]string `json:"topology,omitempty"`
	Scenario   *Scenario         `json:"scenario,omitempty"`
	Step       int               `json:"step"`
	Host       string            `json:"host,omitempty"`
	StepOutput *CommandOutput    `json:"output,omitempty"`
}

func (client *Client) HandleRequest(p *Packet, env Environment) {

	if p.Opcode == "ping" {
		client.Logger.Printf("received ping")
		client.SendPacket(Packet{
			Opcode:   "pong",
			Sequence: p.Sequence,
		})
		return
	}
	if p.Opcode != "script" {
		client.Logger.Printf("invalid packet")
		return
	}

	// If we got this far, we have a script to run, make sure
	// our matcher agrees we should perform this command.
	matcher := p.Scenario.Matcher.DescriptionToMatcher()
	if !matcher.Validate(env) {
		client.Logger.Printf("received script, denying execution")
		client.SendPacket(Packet{
			Opcode:   "command-deny",
			Sequence: p.Sequence,
			Message:  "not a valid target for matcher.",
		})
		return
	}

	client.Logger.Printf("received script, starting.")
	client.SendPacket(Packet{
		Opcode:   "command-start",
		Sequence: p.Sequence,
		Message:  "starting execution.",
	})

	// If we got this far, we can run the script.
	for i, cmddesc := range p.Scenario.Commands {
		cmd := cmddesc.DescriptionToCommand()
		cmdout := cmd.Execute()
		client.Logger.Printf("sending step %v output.", i)
		client.SendPacket(Packet{
			Opcode:     "command-step",
			Sequence:   p.Sequence,
			Step:       i,
			StepOutput: &cmdout,
		})
		if !cmdout.Success {
			break
		}
	}
	client.Logger.Printf("script finished.")
	client.SendPacket(Packet{
		Opcode:   "command-end",
		Sequence: p.Sequence,
		Message:  "execution finished.",
	})
}
