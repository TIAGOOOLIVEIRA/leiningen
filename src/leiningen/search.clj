(ns leiningen.search
  "Search Central and Clojars for published artifacts."
  (:require [clojure.string :as string]
            [leiningen.core.project :as project]
            [cheshire.core :as json])
  (:import (java.net URLEncoder)))

(defn search-central [query]
  (let [url (str "https://search.maven.org/solrsearch/select?wt=json&q=" query)]
    (doseq [result (get-in (json/decode (slurp url))
                           ["response" "docs"])]
      (let [dep (if (= (result "a") (result "g"))
                  (result "a")
                  (str (result "g") "/" (result "a")))]
        (println (format "[%s \"%s\"]" dep (result "latestVersion")))))))

(defn search-clojars [query]
  (let [url (str "https://clojars.org/search?format=json&q=" query)]
    (doseq [result (get (json/decode (slurp url)) "results")]
      (let [dep (if (= (result "jar_name") (result "group_name"))
                  (result "jar_name")
                  (str (result "group_name") "/" (result "jar_name")))]
        (println (format "[%s \"%s\"]" dep (result "version")))
        (when-let [desc (and (not= (string/trim (result "description" "")) "")
                             (result "description"))]
          (println " " (string/trim (first (string/split desc #"\n")))))))))

(defn ^:no-project-needed search
  "Search Central and Clojars for published artifacts."
  [project query]
  (let [project (or project (project/make {}))
        repos (into {} (:repositories project))]
    (doseq [[repo searcher] [["central" search-central]
                             ["clojars" search-clojars]]]
      (when (repos repo)
        (try (println "Searching" repo "...")
             (searcher (URLEncoder/encode query "UTF-8"))
             (catch java.io.IOException e
               (binding [*out* *err*]
                 (if (re-find #"HTTP response code: (400|505)" (str e))
                   (println "Query syntax unsupported.")
                   (println "Remote error" (.getMessage e))))))))))
