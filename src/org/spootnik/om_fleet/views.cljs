(ns org.spootnik.om-fleet.views
  (:require [om.core :as om :include-macros true]
            [cljs.core.async :refer [chan <! >! put!]]
            [ajax.core :refer [GET POST PUT ajax-request]]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as dom :include-macros true]
            [om-bootstrap.nav :as n]
            [om-bootstrap.panel :as p]
            [om-bootstrap.button :as b]
            [om-bootstrap.random :as r]
            [org.spootnik.om-fleet.utils :refer [redirect]]
            ))

(defn state-from-event
  [app k]
  (let [f (fn [app k e]
            (om/transact! app k #(-> e .-target .-value)))]
    (partial f app k)))

(defcomponent nav
  [app owner]
  (render
    [this]
    (n/navbar {:brand "Fleet" :static-top? true}
              (n/nav
                {:active-key (get-in app [:router :tab])}
                (n/nav-item {:href "#/scenarios" :key :scenarios} "Scenarios")))))

(defn refresh
  [chan resource]
  (put! chan {:resource resource :action :refresh}))

(defn schedule-scenario [scenario profile chan e]
  (put! chan {:resource :scenarios :action :execute :script scenario :profile profile}))

(defcomponent scenario
  [[scn-name data] owner]
  (render
    [this]
    (dom/tr nil
      (dom/td nil (dom/a {:href (str "#/scenarios/" scn-name)} scn-name))
      (dom/td nil (data "timeout"))
      (dom/td nil (dom/code nil (data "match")))
      (dom/td nil
        (b/button {:bs-style "primary"
                   :bs-size "xsmall"
                   :on-click (partial schedule-scenario data :default (om/get-shared owner :sync))}
                  "execute")))))

(defcomponent scenarios
  [scns owner]
  (will-mount
    [this]
    (refresh (om/get-shared owner :sync) :scenarios))

  (render
    [this]
    (p/panel {:header (dom/h3 {:class "modal-title"} "Scenarios")}
      (dom/table {:class "table table-striped"}
        (dom/thead nil
          (dom/tr nil
            (dom/th nil "Script name")
            (dom/th nil "Timeout")
            (dom/th nil "Match")
            (dom/th nil "Action")))
        (dom/tbody nil (om/build-all scenario scns))))))

(defcomponent results
  [result owner]
  (render
    [this]
    (when result
      (dom/div nil "lol"))))

(defcomponent scenarios-list
  [app owner]
  (render
    [this]
    (dom/div
      (om/build scenarios (:scenarios app))
      (om/build results (:results app)))))

(defcomponent profile
  [[profile data] owner]
  (render
    [this]
    (dom/tr nil
      (dom/td nil profile)
      (dom/td nil (dom/code (data "match")))
      (dom/td nil (b/button {:bs-size "xsmall"
                             :bs-style "primary"
                             :on-click (fn [e]
                                         (schedule-scenario
                                           @(om/get-shared owner :scenario)
                                           (keyword profile)
                                           (om/get-shared owner :sync)
                                           e))}
                            "execute")))))

(def colors {"finished" "primary"
             "failure" "danger"
             "success" "success"})

(defcomponent step-row
  [{:strs [status]} owner]
  (render
    [this]
    (dom/td nil
      (r/label {:bs-style (colors status "warning")} status))))

(defcomponent host-history
  [[host steps] owner opts]
  (render-state
    [this {:keys [scenario]}]
    (dom/tr nil
      (dom/td nil
        (dom/a {:href (str "#/scenarios/" scenario "/" host)} host))
      (om/build-all step-row steps))))

(defcomponent step-header
  [script owner]
  (render
    [this]
    (dom/th nil
      (r/label {:bs-style "primary"}
        (cond
          (string? script) (cond
                             (= script "ping") script
                             :else "shell")
          (script "service") (str "service" (script "action"))
          (script "sleep") "sleep"
          (script "shell") "shell"
          :else "unknown")) " "
      (cond
        (string? script) (when (not= script "ping") (dom/code nil script))
        (script "service") (script "service")
        (script "sleep") (script "sleep")
        (script "shell") (dom/code nil (script "shell"))))))

(defcomponent scenario-history
  [history owner]
  (render-state
    [this state]
    (let [history (assoc history "hostsv" (->> (history "hostsv")
                                              (sort-by #(first %))))]
      (dom/div nil
        (dom/h4 nil (str "History (" (history "total_done") " out of " (history "starting_hosts") " done)"))
        (dom/table {:class "table table-striped"}
          (dom/thead nil
            (dom/tr nil
              (dom/th nil "Host")
              (om/build-all step-header (history "script"))
              (dom/th nil "Complete")))
          (dom/tbody nil
            (om/build-all host-history (history "hostsv") {:state state})))))))

(defcomponent scenario-step
  [step owner]
  (render
    [this]
    (dom/div nil
      (cond
        (string? step)
        (cond
          (= step "ping")
          (dom/p nil (r/label {:bs-style "primary"} "ping"))
          
          :else
          (dom/pre {:class "console"} step))

        (step "shell")
        (dom/pre {:class "console"} (step "shell"))

        (step "service")
        (dom/span nil
                  (dom/p nil
                         (r/label {:bs-style "primary"} "service")
                         " "
                         (dom/code nil (step "service"))
                         " "
                         (r/label {:bs-style "info"} (step "action"))))

        (step "sleep")
        (dom/p nil (r/label {:bs-style "primary"} "sleep")
               " " (dom/code nil (step "sleep")))))))

(defcomponent scenario-panel
  [[scenario history] owner]
  (render
    [this]
    (p/panel {:header
       (dom/h3 {:class "modal-title"}
         (dom/a {:href "#/scenarios"} "Scenarios")
         (dom/span nil (str " / " (scenario "script_name"))))}
      (dom/h4 nil "Script")
      (om/build-all scenario-step (scenario "script"))
      (dom/h4 nil "Match")
      (dom/code nil (scenario "match"))
      (when-not (empty? (scenario "profiles"))
        (dom/div nil
          (dom/h4 nil "Profiles")
          (dom/table {:class "table table-striped"}
            (dom/thead nil
              (dom/tr nil
                (dom/th nil "Name")
                (dom/th nil "Match")
                (dom/th nil "Action")))
            (dom/tbody nil
              (om/build-all profile (scenario "profiles") {:shared {:scenario scenario
                                                                    :sync (om/get-shared owner :sync)
                                                                    }})))))
      (when-not (or (nil? history) (nil? (history "id")))
        (om/build scenario-history history {:state {:scenario (scenario "script_name")}})))))

(defcomponent scenario-detail
  [app owner]
  (will-mount
    [this]
    (let [chan (om/get-shared owner :sync)
          scenario (get-in app [:router :route-params :scenario])
          current (get-in app [:scenario "script_name"])]
      (when-not (= current scenario)
        (do
          (om/transact! app :scenario
                        (fn  [_]  {"script_name" scenario}))
          (om/transact! app :history (fn [_] {}))
          (put! chan {:resource :scenarios :action :get :id scenario})))))

  (render
    [this]
    (let [scn (:scenario app)]
      (when-not (nil? scn)
        (om/build scenario-panel [(:scenario app) (get-in app [:history (scn "script_name")])])))))

(defcomponent output
  [{:keys [typ out]} owner]
  (render
    [this]
    (dom/div nil
      (dom/pre
        {:class "console" :dangerouslySetInnerHTML #js {:__html (str "<span style='color:#777;'>" typ "&gt;</span>\n" out)}}))))

(defcomponent host-history-detail
  [{:strs [status code stderr stdout script]} owner]
  (render
    [this]
    (dom/div nil
    (dom/h4 nil
      (cond
        (= "ping" script) "Ping "
        (nil? script) ""
        :else (dom/span nil "Script " (dom/code nil script) " "))
      (r/label {:bs-style (colors status)} status))
    (when-not (empty? stdout)
      (om/build output {:typ "stdout" :out stdout}))
    (when-not (empty? stderr)
      (om/build output {:typ "stderr" :out stderr})))))

(defcomponent scenario-host-history
  [app owner]
  (will-mount
    [this]
    (let [chan (om/get-shared owner :sync)
          scenario (get-in app [:router :route-params :scenario])]
      (when-not (get-in app [:history scenario])
        (do
          (put! chan {:resource :scenarios :action :get :id scenario})))))
  
  (render
    [this]
    (let [scenario (get-in app [:router :route-params :scenario])
          host (get-in app [:router :route-params :host])
          [[_ data]] (filter #(= host (first %)) (get-in app [:history scenario "hostsv"]))]
      (p/panel {:header (dom/h4 nil
                                (dom/a {:href "#/scenarios"} "Scenarios")
                                (dom/span nil " / ")
                                (dom/a {:href (str "#/scenarios/" scenario)} scenario)
                                (dom/span nil (str " / " host))

                              )}
               (dom/div nil (om/build-all host-history-detail data))))))

(defcomponent index
  [app owner]
  (will-mount
    [this]
    (redirect (:h app) "/scenarios"))

  (render
    [this]))
