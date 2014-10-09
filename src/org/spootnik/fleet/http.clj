(ns org.spootnik.fleet.http
  (:require [compojure.core                 :refer [routes GET POST PUT DELETE]]
            [clojure.tools.logging          :refer [error]]
            [clojure.pprint                 :refer [pprint]]
            [ring.util.response             :refer [response redirect]]
            [cheshire.core                  :refer [generate-string]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params         :refer [wrap-params]]
            [ring.middleware.json           :as json]
            [compojure.route                :as route]
            [clojure.core.async             :as async]
            [org.spootnik.fleet.api         :as api]
            [org.spootnik.fleet.engine      :as engine]
            [org.spootnik.fleet.history     :as history]
            [org.httpkit.server             :as http]
            [ring.middleware.cors           :refer [wrap-cors cors-hdrs]]))

(defn json-response
  [body]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (generate-string body)})

(defn api-routes
  [scenarios engine {:keys [origins] :or {origins []}}]

  (let [patterns (mapv re-pattern origins)]
    (routes
     (GET "/scenarios" []
          (json-response (api/all! scenarios)))

     (POST "/scenarios" {scenario :body}
           (json-response (api/upsert! scenarios scenario)))

     (PUT "/scenarios" []
          (json-response (api/save! scenarios)))

     (GET "/scenarios/:script_name" [script_name]
          (json-response (api/get! scenarios script_name)))

     (DELETE "/scenarios/:script_name" [script_name]
             (json-response (api/delete! scenarios script_name)))

     (GET "/scenarios/:script_name/history" [script_name]
          (json-response (history/fetch scenarios script_name)))

     (GET "/scenarios/:script_name/executions" request
          (let [script_name (-> request :params :script_name)
                args        (if-let [args (-> request :params :args)]
                              (if (sequential? args) args [args])
                              [])
                scenario    (api/get! scenarios script_name)
                profile     (-> request :params :profile keyword)
                ch          (async/chan 10)
                headers     {"Content-Type" "text/event-stream"
                             "X-Accel-Buffering" "no"
                             "Cache-Control" "no-cache"}
                origin      (get-in request [:headers "origin"])
                valid-cors  (and origin (some #(re-find % origin) patterns))
                headers     (if valid-cors
                              (merge (cors-hdrs (request :headers)) headers)
                              headers)]
            (http/with-channel request hchan
              (future
                (try
                  (doseq [msg (repeatedly #(async/<!! ch))
                          :while msg
                          :when msg]
                    (history/update script_name msg)
                    (http/send! hchan
                                {:status 200
                                 :headers headers
                                 :body (format "data: %s\n\n"
                                               (generate-string msg))}
                                false))
                  (http/close hchan)
                  (catch Exception e
                    (error e "cannot handle incoming message"))))
              (engine/request engine
                              (api/prepare scenario profile args)
                              ch))))

     ;; defaults
     (GET "/" []      (redirect "/index.html"))
     (route/resources "/")
     (route/not-found {:status 404 :body
                       (generate-string {:status "no such resource"})}))))

(defn wrap-error
  [handler]
  (fn [request]
    (try (handler request)
         (catch Exception e
           (let [{:keys [status] :as data} (ex-data e)]
             (when (nil? data) (error e "unhandled exception"))
             {:status (or status 500)
              :body (generate-string {:status (.getMessage e)})})))))

(defn yield-cors-match
  [{:keys [origins] :or {origins []}}]
  (let [patterns (mapv re-pattern origins)]
    (fn [origin]
      (and origin (some #(re-find % origin) patterns)))))

(defn start-http
  [scenarios engine opts]
  (api/load! scenarios)
  (http/run-server (-> (api-routes scenarios engine opts)
                       (wrap-error)
                       (wrap-keyword-params)
                       (wrap-params)
                       (wrap-cors (yield-cors-match opts))
                       (json/wrap-json-body {:keywords? true}))
                   opts))
