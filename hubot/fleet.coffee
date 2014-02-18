# Description:
#   Interacts with fleet: the parallel execution commander
#
# Commands:
#   hubot fleet me <scenario> - Schedules scenario for execution by fleet
#
# Configuration:
#   HUBOT_FLEET_URL - contains fleet url
#

class Fleet
  acks: 0
  ack_starting: 0
  acks_done: false
  done: 0

  constructor: (@scenario, @client) ->
    history = process.env.HUBOT_FLEET_URL + '#/history/' + @scenario
    @client.send "executing " + scenario + " (waiting 2 seconds for acks, reporting to: " + history + ')'

  process: (msg) ->

    if ((msg.type == 'resp' || msg.type == 'stop') && ! @acks_done)
      @acks_done = true
      @client.send @scenario + ": got " + @ack_starting + "/" + @acks + " positive acknowledgements"

    if (msg.type == 'ack')
      @acks++
      if (msg.msg.status == 'starting')
        @ack_starting++

    if (msg.type == 'resp')
      if (msg.msg.output.status == 'finished')
        @done++
        @client.send @scenario + ': ' + msg.msg.host + ': success (' + @done + '/' + @ack_starting + ')'
      if (msg.msg.output.status == 'failure')
        @done++
        @client.send @scenario + ': ' + msg.msg.host + ': failure! (' + @done + '/' + @ack_starting + ')'

      if (@done >= @ack_starting)
        @client.send @scenario + ": all done!"



module.exports = (robot) ->

  fleet_url = process.env.HUBOT_FLEET_URL

  robot.respond /fleet me (\w+)( to (\w+))?( with (.*))?/i, (msg) ->

    scenario = msg.match[1]
    args = []
    if msg.match[3]
      args.push('profile=' + msg.match[3])

    if msg.match[5]
      args.push('args=' + arg) for arg in msg.match[5].split(" ")

    fleet = new Fleet(scenario, msg)
    url = fleet_url + "/scenarios/" + scenario + "/executions"
    if args.length > 0
      url += '?' + args.join("&")
    msg.http(url).get((err, req) ->
      req.addListener 'response', (resp) ->
        resp.addListener 'data', (chunk) ->
          data = chunk.toString('utf8').substr(6)
          fleet.process(JSON.parse(data)))()
