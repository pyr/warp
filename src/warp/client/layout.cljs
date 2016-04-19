(ns warp.client.layout
  (:require [clojure.string :as str]))

(defn h4
  [& fragments]
  [:h4 (str/join " " fragments)])

(defn h3
  [& fragments]
  [:h3 (str/join " " fragments)])

(defn h2
  [& fragments]
  [:h2 (str/join " " fragments)])

(defn h1
  [& fragments]
  [:h1 (str/join " " fragments)])

(defn link-to
  [url link]
  [:a {:href url} link])

(defn code
  [& fragments]
  [:code (str/join " " fragments)])

(defn console
  [& fragments]
  [:pre {:class "console"} (str/join " " fragments)])

(defn tr
  [& row]
  [:tr
   (for [[i td] (map-indexed vector row)]
     ^{:key (str "tr-" i)} [:td td])])

(defn panel
  [title content]
  [:div {:class "panel panel-default"}
   [:div {:class "panel-heading"}
    [:h3 {:class "modal-title panel-title"} title]]
   [:div {:class "panel-body"}
    content]])

(defn table-striped
  [columns rows]
  [:table {:class "table table-striped"}
   [:thead
    [:tr (for [[i th] (map-indexed vector columns)]
           ^{:key (str "th-" i)} [:th th])]]
   [:tbody rows]])
