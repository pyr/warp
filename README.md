fleet: distributed workflow management
======================================

fleet distributes scenarios across any number of machines.

### The story

Your applications span a large group of hosts and deployment
involves several separate steps.

Let's say that you have a web application deployment process
that involves:

- Updating a git repository
- Restarting a service

This process is likely the same for several profiles such as
*test*, *staging* and *production*.

![fleet scenarios](http://i.imgur.com/6svdQH9.png)

fleet provides a DSL for writing scenarios and schedules
executions over a pub-sub system, streaming the results
to the controller which makes results available through
an API and web view

Command executions can be scheduled through the following means:

- The web interface exposed by the controller
- API queries to the controller
- IRC/Campfire/Hipchat through a [hubot](http://hubot.github.io) script

### A sample scenario

```yaml
## We give the scenario a name
script_name: webapp-deploy

## A matcher determines how 
match:
  and:
    - fact: "platform"
      value: "webapp-hosts"
    - fact: "environment"
      value: "staging"
script:
  - "apt-get update"
  - shell: "puppet agent -t"
    exits: [0, 2]
  - "apt-get install webapp"
  - service: "webapp"
    action: "reload"
timeout: 15000
profiles:
  production:
    match:
      and:
        - fact: "platform"
          value: "webapp-hosts"
        - fact: "environment"
          value: "production"
```

## More screenshots

![fleet index](http://i.imgur.com/qawWTTX.png)
![fleet output](http://i.imgur.com/sYVRCHf.png)



### Pub-Sub support

Currently, fleet relies on the redis pub-sub functionnality
for execution. 
fleet works hand in hand with any number of
[fleet-agent's](https://github.com/pyr/fleet-agent), schedules
scenarios for execution.

scenario creation an execution happens through an HTTP api,
a command-line client is coming up.

fleet and [fleet-agent](https://github.com/pyr/fleet-agent) borrow
from [mcollective](http://puppetlabs.com/mcollective) and my first
implementation within [amiral](https://github.com/pyr/amiral)

fleet aims to improve on amiral in the following ways:

- Bundle matchers, timeouts and a list of commands (a script) options
  in named "scenarios"
- Extract out of the IRC bot framework and display extended execution
  results in a web view
- Deprecate signing requests with ssh-keys and move to SSL
- Provide a lighter-weight agent
- Support arguments

