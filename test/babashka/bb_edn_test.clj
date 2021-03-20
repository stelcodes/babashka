(ns babashka.bb-edn-test
  {:clj-kondo/config '{:linters {:unresolved-symbol {:exclude [working?]}}}}
  (:require
   [babashka.fs :as fs]
   [babashka.test-utils :as test-utils]
   [clojure.edn :as edn]
   [clojure.test :as test :refer [deftest is testing]]
   [clojure.string :as str]))

(defn bb [& args]
  (edn/read-string
   {:readers *data-readers*
    :eof nil}
   (apply test-utils/bb nil (map str args))))

(defmacro with-config [cfg & body]
  `(let [temp-dir# (fs/create-temp-dir)
         bb-edn-file# (fs/file temp-dir# "bb.edn")]
     (spit bb-edn-file# ~cfg)
     (binding [test-utils/*bb-edn-path* (str bb-edn-file#)]
       ~@body)))

(deftest babashka-task-test
  (with-config {:tasks {:sum {:task/type :babashka
                              :args ["-e" "(+ 1 2 3)"]}}}
    (let [res (bb :sum)]
      (is (= 6 res)))))

(deftest shell-task-test
  (let [temp-dir (fs/create-temp-dir)
        temp-file (fs/create-file (fs/path temp-dir "temp-file.txt"))]
    (with-config {:tasks {:clean {:task/type :shell
                                  :args ["rm" (str temp-file)]}}}
      (is (fs/exists? temp-file))
      (bb :clean)
      (is (not (fs/exists? temp-file))))))

(deftest do-task-test
  (testing ":and-do"
    (let [temp-dir (fs/create-temp-dir)
          temp-file (fs/create-file (fs/path temp-dir "temp-file.txt"))]
      (with-config {:tasks {:clean {:task/type :shell
                                    :args ["rm" (str temp-file)]}
                            :sum {:task/type :babashka
                                  :args ["-e" "(+ 1 2 3)"]}
                            :all {:task/type :babashka
                                  :args [:do :clean :and-do :sum]}}}
        (is (fs/exists? temp-file))
        (let [res (bb :all)]
          (is (= 6 res)))
        (is (not (fs/exists? temp-file))))))
  (testing ":do always continuing"
    (with-config {:tasks {:sum-1 {:task/type :babashka
                                  :args ["-e" "(do (+ 4 5 6) nil)"]}
                          :sum-2 {:task/type :babashka
                                  :args ["-e" "(+ 1 2 3)"]}
                          :all {:task/type :babashka
                                :args [:do :sum-1 :do :sum-2]}}}
      (is (= 6 (bb :all))))
    (with-config {:tasks {:div-by-zero {:task/type :babashka
                                        :args ["-e" "(/ 1 0)"]}
                          :sum {:task/type :babashka
                                :args ["-e" "(+ 1 2 3)"]}
                          :all {:task/type :babashka
                                :args [:do :div-by-zero :do :sum]}}}
      (is (= 6 (bb :all)))))
  (testing ":and-do failing"
    (with-config {:tasks {:div-by-zero {:task/type :babashka
                                        :args ["-e" "(/ 1 0)"]}
                          :sum {:task/type :babashka
                                :args ["-e" "(+ 1 2 3)"]}
                          :all {:task/type :babashka
                                :args [:do :div-by-zero :and-do :sum]}}}
      (is (thrown-with-msg? Exception #"Divide"
                            (bb :all)))))
  (testing ":or-do short-cutting"
    (with-config {:tasks {:sum-1 {:task/type :babashka
                                :args ["-e" "(+ 1 2 3)"]}
                          :sum-2 {:task/type :babashka
                                  :args ["-e" "(+ 4 5 6)"]}
                          :all {:task/type :babashka
                                :args [:do :sum-1 :or-do :sum-2]}}}
      (is (= 6 (bb :all)))))
  (testing ":or-do succeeding after failing"
    (with-config {:tasks {:div-by-zero {:task/type :babashka
                                        :args ["-e" "(/ 1 0)"]}
                          :sum {:task/type :babashka
                                :args ["-e" "(+ 1 2 3)"]}
                          :all {:task/type :babashka
                                :args [:do :div-by-zero :or-do :sum]}}}
      (is (= 6 (bb :all))))))

(deftest priotize-user-task-test
  (is (map? (bb :describe)))
  (with-config {:tasks {:describe {:task/type :babashka
                                   :args ["-e" "(+ 1 2 3)"]}}}
    (is (= 6 (bb :describe)))))

(deftest help-task-test
  (with-config {:tasks {:cool-task {:task/type :babashka
                                    :args ["-e" "(+ 1 2 3)"]
                                    :task/help "Usage: bb :cool-task

Addition is a pretty advanced topic.  Let us start with the identity element
0. ..."}}}
    (is (str/includes? (apply test-utils/bb nil
                              (map str [:help :cool-task]))
                       "Usage: bb :cool-task"))))

(deftest list-tasks-test
  (with-config {:tasks {:cool-task-1 {:task/type :babashka
                                    :args ["-e" "(+ 1 2 3)"]
                                    :task/help "Usage: bb :cool-task

Addition is a pretty advanced topic.  Let us start with the identity element
0. ..."}
                        :cool-task-2 {:task/type :babashka
                                      :args ["-e" "(+ 1 2 3)"]
                                      :task/help "Usage: bb :cool-task

Addition is a pretty advanced topic.  Let us start with the identity element
0. ..."}}}
    (let [res (apply test-utils/bb nil
                     (map str [:tasks]))]
      (is (str/includes? res "The following tasks are available:"))
      (is (str/includes? res ":cool-task-1"))
      (is (str/includes? res ":cool-task-2")))))
