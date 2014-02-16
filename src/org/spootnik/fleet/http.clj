(ns org.spootnik.fleet.http
  (:require [compojure.core            :refer [routes GET POST PUT DELETE]]
            [clojure.tools.logging     :refer [error]]
            [clojure.pprint            :refer [pprint]]
            [ring.util.response        :refer [response redirect]]
            [cheshire.core             :refer [generate-string]]
            [ring.middleware.json      :as json]
            [compojure.route           :as route]
            [clojure.core.async        :as async]
            [org.spootnik.fleet.api    :as api]
            [org.spootnik.fleet.engine :as engine]
            [org.httpkit.server        :as http]))

(defn api-routes
  [scenarios engine]

  (routes
   (GET "/scenarios" []
        (response (generate-string (api/all! scenarios))))

   (POST "/scenarios" {scenario :body}
         (response (generate-string (api/upsert! scenarios scenario))))

   (PUT "/scenarios" []
        (response (generate-string (api/save! scenarios))))

   (GET "/scenarios/:script_name" [script_name]
        (response (generate-string  (api/get! scenarios script_name))))

   (DELETE "/scenarios/:script_name" [script_name]
           (response (generate-string (api/delete! scenarios script_name))))

   (GET "/scenarios/:script_name/executions" request
        (let [script_name (-> request :params :script_name)
              scenario (api/get! scenarios script_name)
              ch       (async/chan 10)]
          (http/with-channel request hchan
            (future
              (doseq [msg (repeatedly #(async/<!! ch))
                      :while msg
                      :when msg]
                (http/send! hchan
                            {:status 200
                             :headers {"Content-Type" "text/event-stream"
                                       "Cache-Control" "no-cache"}
                             :body (format "data: %s\n\n"
                                           (generate-string msg))}
                            false))
              (http/close hchan))
            (engine/request engine scenario ch))))

   ;; defaults
   (GET "/" []      (redirect "/index.html"))
   (route/resources "/")
   (route/not-found {:status 404 :body
                     (generate-string {:status "no such resource"})})))

(defn wrap-error
  [handler]
  (fn [request]
    (try (handler request)
         (catch Exception e
           (let [{:keys [status] :as data} (ex-data e)]
             (when (nil? data) (error e "unhandled exception"))
             {:status (or status 500)
              :body (generate-string {:status (.getMessage e)})})))))

(defn start-http
  [scenarios engine opts]
  (api/load! scenarios)
  (http/run-server (-> (api-routes scenarios engine)
                       (wrap-error)
                       (json/wrap-json-body {:keywords? true}))
                   opts))
