var app = angular.module('fleet', ['ngRoute', 'ngSanitize']);

app.filter('ansi', function($sce) {
    return function (input) {
        if (input)
            return $sce.trustAsHtml(window.ansi_up.ansi_to_html(
                window.ansi_up.escape_for_html(input)));
        else
            return "";
    };
});

app.filter('matcher', function () {

    return function recur (input)  {
       if (! input)
           return "";

       if (input == "all")
           return "all";

       if (input.host)
           return "host = " + input.host;

       if (input.fact)
           return "facts[" + input.fact + "] = " + input.value;

       if (input.not)
           return "!(" + recur(input.not) + ')';

       if (input.and)
           return '(' + _.map(input.and, recur).join(" && ") + ')';

       if (input.or)
           return '(' + _.map(input.and, recur).join(" || ") + ')';
   }
});

app.factory('$scenario', function ($http, $timeout) {
   return {
       create: function (scenario) {
           return $http.post('/scenarios', scenario);
       },
       list: function() {
           return $http.get('/scenarios');
       },
       delete: function(script_name) {
           return $http.delete('/scenarios/' + script_name);
       },
       history: function(script_name) {
           return $http.get('/scenarios/' + script_name + '/history');
       },
       execute: function (scope, script, script_name) {
           var url = '/scenarios/' + script_name + '/executions';
           var es = new EventSource(url);
           var results = {done: false,
                          script_name: script_name,
                          script: script,
                          hosts: {},
                          host_commands: {},
                          total_acks: 0,
                          starting_hosts: 0,
                          total_done: 0};

           es.onconnect = function () {
               return;
           };
           es.onerror = function (data, why) {
               results.done = true;
               es.close();
           };
           es.onclose = function () {
             results.done = true;
           };
           es.onmessage = function (payload) {
               $timeout(function() {

                   var msg = JSON.parse(payload.data);

                   if (msg.type == 'ack') {
                       if (msg.msg.status == 'starting') {
                           results.starting_hosts += 1;
                       }
                       results.total_acks += 1;
                   } else if (msg.type == 'resp') {
                       var host = msg.msg.host;
                       var finished = ((msg.msg.output.status == 'failure') ||
                                       (msg.msg.output.status == 'finished'));

                       if (!results.hosts[host])
                           results.hosts[host] = [];
                       results.hosts[host].push(msg.msg.output);

                       if (finished)
                           results.total_done += 1;

                       if (results.total_done >= results.starting_hosts)
                           results.done = true;

                       results.host_commands[host] = _.zip(results.script, results.hosts[host]);
                   } else {
                       console.log('unknown payload type', msg);
                   }
               });
           };
           return results;
       }
   };
});

app.controller('FleetHistory', function($scope, $routeParams, $scenario, $timeout) {
    $scope.get_color = function(c) {
        if (c == 'finished') {
            return 'label-primary';
        } else if (c == 'failure') {
            return 'label-danger';
        } else if (c == 'success') {
            return 'label-success';
        } else {
            return 'label-warning';
        }
    };

    $scope.is_script = function(input) {
        return ((typeof input == 'string') && (input != 'ping'));
    };

    $scope.set_host = function(host) {
        $scope.host = host;
    }

    $scope.nolist = true;
    $scope.host = undefined;
    $scope.empty_body = _.isEmpty;

    $timeout(function () {
        $scenario.history($routeParams.script_name).success(function (data) {
            data.host_commands = {};
            _.each(data.hosts, function(v,k) {
                data.host_commands[k] = _.zip(data.script, data.hosts[k]);
            });
            $scope.execution = data;
        });
    });
});

app.controller('Fleet', function($scope, $routeParams, $location, $scenario) {

    $scope.get_color = function(c) {
        if (c == 'finished') {
            return 'label-primary';
        } else if (c == 'failure') {
            return 'label-danger';
        } else if (c == 'success') {
            return 'label-success';
        } else {
            return 'label-warning';
        }
    };

    $scope.is_script = function(input) {
        return ((typeof input == 'string') && (input != 'ping'));
    };

    $scope.scenarios = {};

    $scope.empty_body = _.isEmpty;

    $scenario.list().success(function (data) {
        $scope.scenarios = data;
    });

    if ($routeParams.script_name) {
        $scope.script_name = $routeParams.script_name;
    }

    if ($routeParams.host) {
        $scope.host = $routeParams.host;
    }

    $scope.set_host = function(host) {
        $scope.host = host;
    }

    $scope.scenario_list = function() {
        var l = [];
        for (var k in $scope.scenarios) {
            l.push($scope.scenarios[k]);
        }
        return l;
    };

    $scope.create_scenario = function (scenario) {
        $scenario.create(scenario).success(function (data) {
            $scope.scenarios = data;
            $location.path('/scenarios');
        });
    };

    $scope.delete_scenario = function (script_name) {
        $scenario.delete(script_name).success(function (data) {
            $scope.scenarios = data;
            $location.path('/scenarios');
        });
    };

    $scope.execute_scenario = function (script_name) {
        $scope.execution_scheduled = $scope.scenarios[script_name];
        $scope.execution = $scenario.execute($scope, $scope.scenarios[script_name].script, script_name);
    };
});

app.config(function($routeProvider) {
    $routeProvider
        .when('/list', {templateUrl: 'listing.html', controller: 'Fleet'})
        .when('/details/:script_name', {templateUrl: 'details.html', controller: 'Fleet'})
        .when('/host/:host', {templateUrl: 'host.html', controller: 'Fleet'})
        .when('/post', {templateUrl: 'post.html', controller: 'Fleet'})
        .when('/history/:script_name', {templateUrl: 'listing.html', controller: 'FleetHistory'})
        .otherwise({redirectTo: '/list'});
});
