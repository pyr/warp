VERSION=0.3.0-snapshot
PREFIX?=/usr/local
GOPATH=$(PWD)/build:$(PWD)
PROGRAM=warp-agent
GO=env GOPATH=$(GOPATH) go
SRCS=   src/warp/matcher.go				\
	src/warp/environment.go				\
	src/warp/request.go				\
	src/warp/client.go				\
	src/warp/config.go				\
	src/warp/command.go

RM?=rm -f
LN=ln -s
MAIN=warp-agent.go

all: $(PROGRAM)

$(PROGRAM): $(MAIN) $(SRCS)
	$(GO) build -o $(PROGRAM) $(MAIN)

clean:
	$(RM) $(PROGRAM)
	$(GO) clean

cleandeps: clean
	$(RM) $(PWD)/build

#deps:
#	$(GO) get gopkg.in/yaml.v2
