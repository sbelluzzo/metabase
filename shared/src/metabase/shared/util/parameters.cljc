(ns metabase.shared.util.parameters
  "Util functions for dealing with parameters"
  (:require [clojure.string :as str]
            [metabase.mbql.normalize :as mbql.normalize]))

(def ^:private template-tag-regex
  "A regex to find template tags in a text card on a dashboard. This should mirror the regex used to find template
  tags in native queries, with the exception of snippets and card ID references (see the metabase-lib function
  `recognizeTemplateTags` for that regex)."
  #"\{\{\s*([A-Za-z0-9_\.]+?)\s*\}\}")

(defn ^:export tag_names
  "Given the context of a text dashboard card, return a set of the unique names of template tags in the text."
  [text]
  (let [tag-set (->> (re-seq template-tag-regex text)
                     (map second)
                     set)]
    #? (:clj tag-set
        :cljs (clj->js tag-set))))

(defn- normalize-parameter
  "Normalize a single parameter by calling [[mbql.normalize/normalize-fragment]] on it, and converting all string keys
  to keywords."
  [parameter]
  (->> (mbql.normalize/normalize-fragment [:parameters] [parameter])
       first
       (reduce-kv (fn [acc k v] (assoc acc (keyword k) v)) {})))

(defmulti formatted-value
  "Formats a value appropriately for inclusion in a text card, based on its type. Does not do any escaping."
  (fn [tyype _value] (keyword tyype)))

(defmethod formatted-value :default
  [_ value]
  (cond
    (and (sequential? value) (> (count value) 1))
    (str/join ", " value)

    (sequential? value)
    (first value)

    :else
    value))

(def ^:private escaped-chars-regex
  #"[\\/*_`'\[\](){}<>#+-.!$@%^&=|\?~]")

(defn- escape-chars
  [text]
  (str/replace text escaped-chars-regex #(str \\ %)))

(defn- replacement
  [tag->param match]
  (let [tag-name (second match)
        param    (get tag->param tag-name)
        value    (:value param)
        tyype    (:type param)]
    (if value
      (-> (formatted-value tyype value)
          escape-chars)
      ;; If this parameter has no value, return the original {{tag}} so that no substitution is done.
      (first match))))

(defn ^:export substitute_tags
  "Given the context of a text dashboard card, replace all template tags in the text with their corresponding values,
  formatted and escaped appropriately."
  [text tag->param]
  (let [tag->param #?(:clj tag->param
                      :cljs (js->clj tag->param))
        tag->normalized-param (reduce-kv (fn [acc tag param]
                                           (assoc acc tag (normalize-parameter param)))
                                         {}
                                         tag->param)]
     (str/replace text template-tag-regex (partial replacement tag->normalized-param))))
