{:transport {:port 6380
             :ssl  {:ca-cert "/etc/warp/certs/ca-crt.pem"
                    :cert    "/etc/warp/certs/server.pem"
                    :pkey    "/etc/warp/certs/server.pkey64.p8"}}
 :api       {:port 6381}
 :scenarios "/etc/warp/scenarios"
 :keepalive 30}
