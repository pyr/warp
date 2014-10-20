(ns org.spootnik.warp.api-test
  (:require [org.spootnik.warp.api :refer :all]
            [clojure.test          :refer :all]))

(deftest match-test
  (testing "simple-match"
    (is (= "all" (prepare-match [] "all"))))
  (testing "simple replace"
    (is (= "foo bar" (prepare-match ["foo" "bar"] "{{0}} {{1}}"))))
  (testing "and replace"
    (is (= {:and ["all" "foo bar"]}
           (prepare-match ["foo" "bar"]
                          {:and ["all" "{{0}} {{1}}"]}))))
  (testing "or replace"
    (is (= {:or ["all" "foo bar"]}
           (prepare-match ["foo" "bar"]
                          {:or ["all" "{{0}} {{1}}"]})))))
