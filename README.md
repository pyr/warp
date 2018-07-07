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
an API and web view.

Command executions can be scheduled through the following means:

- The web interface exposed by the controller
- API queries to the controller
- IRC/Campfire/Hipchat through a [hubot](http://hubot.github.io) script

## A sample scenario

```clj
{:timeout 2
 :name    "ping"
 :matcher {:type :none}
 :profiles {:everyone {:type :all}
            :platform {:type :and :clauses [{:type :fact :fact "facter.sp_environment" :value "{{0}}"}
                                            {:type :fact :fact "facter.platform" :value "{{1}}"}]}
            :prod     {:type :fact :fact "facter.sp_environment" :value "prod"}
            :preprod  {:type :fact :fact "facter.sp_environment" :value "preprod"}
            :host     {:type :host :host "{{0}}"}}
 :commands [{:type :ping}]}
```

## More screenshots

![warp index](http://i.imgur.com/qawWTTX.png)
![warp output](http://i.imgur.com/sYVRCHf.png)

## Pub-Sub support

Scenario execution happens through an HTTP api.

Warp and [warp-agent](https://github.com/pyr/warp-agent) borrow
from [mcollective](http://puppetlabs.com/mcollective) and my first
implementation within [amiral](https://github.com/pyr/amiral).

Warp aims to improve on amiral in the following ways:

- Bundle matchers, timeouts and a list of commands (a script) options
  in named "scenarios"
- Extract out of the IRC bot framework and display extended execution
  results in a web view
- Deprecate signing requests with ssh-keys and move to SSL
- Provide a lighter-weight agent
- Support arguments

## Development

### Controller

To build and run the controller:

    lein cljsbuild
    lein run -- -f doc/controller.clj

When working on the ClojureScript part, you can automatically rebuild
it when a change happens:

    lein cljsbuild auto

### Agent

To build and run an agent:

    cd agent
    make
    ./warp-agent ../doc/warp-agent.json

### Configuration

#### Certs

You will need 3 certificates/keys:

- a CA
- a private key signed by the CA
- the correspondig public key

Since in Java the `pem` format is not supported, you need to produce a `pkcs8`
format of the private key:

```
openssl pkcs8 -topk8 -nocrypt -in my_private.key -outform der | base64 > my_private.pkey64.p8
```

To send a sample command for the ping scenario:
```
curl -v 'http://localhost:6381/api/scenarios/ping/run?profile=host&matchars=localhost'
```
