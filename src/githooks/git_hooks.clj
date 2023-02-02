;; bb/git_hooks.clj
(ns git-hooks
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.java.shell :refer [sh]]))

(defn changed-files []
  (->> (sh "git" "diff" "--name-only" "--cached")
       :out
       str/split-lines
       (filter seq)
       seq))

(def clojure-extensions #{"clj" "cljx" "cljc" "cljs" "edn"})

(defn clj?
  [s]
  (when s
    (let [extension (last (str/split s #"\."))]
      (clojure-extensions extension))))

(defn hook-text
  [hook]
  (format "#!/bin/sh
# Installed by babashka task on %s

bb hooks %s" (java.util.Date.) hook))

(defn spit-hook
  [hook]
  (println "Installing hook: " hook)
  (let [file (str ".git/hooks/" hook)]
    (spit file (hook-text hook))
    (fs/set-posix-file-permissions file "rwx------")
    (assert (fs/executable? file))))

(defmulti hooks (fn [& args] (first args)))

(defmethod hooks "install" [& _]
  (spit-hook "pre-commit"))

(defmethod hooks "pre-commit" [& _]
  (println "Running pre-commit hook")
  (when-let [files (changed-files)]
    (apply sh "clj-kondo" "--lint" (filter clj? files))))

(defmethod hooks :default [& args]
  (println "Unknown command:" (first args)))
