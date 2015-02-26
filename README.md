warp: distributed workflow management
=====================================

warp distributes scenarios across any number of machines.

> hubot: deploy to production, engage!

![build status](https://travis-ci.org/pyr/warp.svg)

## The story

Your applications span a large group of hosts and deployment
involves several separate steps.

Let's say that you have a web application deployment process
that involves:

- Updating a git repository
- Restarting a service

This process is likely the same for several profiles such as
*test*, *staging* and *production*.

![warp scenarios](http://i.imgur.com/6svdQH9.png)

Warp provides a DSL for writing scenarios and schedules
executions over a pub-sub system, streaming the results
to the controller which makes results available through
an API and web view

Command executions can be scheduled through the following means:

- The web interface exposed by the controller
- API queries to the controller
- IRC/Campfire/Hipchat through a [hubot](http://hubot.github.io) script

## A sample scenario

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

![warp index](http://i.imgur.com/qawWTTX.png)
![warp output](http://i.imgur.com/sYVRCHf.png)

## Pub-Sub support

Currently, warp relies on the redis pub-sub functionnality
for execution. 
warp works hand in hand with any number of
[warp-agent's](https://github.com/pyr/warp-agent), schedules
scenarios for execution.

scenario creation an execution happens through an HTTP api,
a command-line client is coming up.

warp and [warp-agent](https://github.com/pyr/warp-agent) borrow
from [mcollective](http://puppetlabs.com/mcollective) and my first
implementation within [amiral](https://github.com/pyr/amiral)

Warp aims to improve on amiral in the following ways:

- Bundle matchers, timeouts and a list of commands (a script) options
  in named "scenarios"
- Extract out of the IRC bot framework and display extended execution
  results in a web view
- Deprecate signing requests with ssh-keys and move to SSL
- Provide a lighter-weight agent
- Support arguments

## Development

To build and run the controller:

    lein cljsbuild
    lein run -- -f doc/warp.yml

When working on the ClojureScript part, you can automatically rebuild
it when a change happens:

    lein cljsbuild auto

You can run the [agent](https://github.com/pyr/warp-agent) with:

    cabal sandbox init
    cabal install --only-dependencies
    cabal build
    cabal run warp-agent -- -v -f ../warp/doc/warp-agent.json
