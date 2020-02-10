(ns malli.util-test
  (:require [clojure.test :refer [deftest testing is are]]
            [malli.util :as mu]))

(deftest equals-test
  (is (true? (mu/equals int? int?)))
  (is (true? (mu/equals [:map [:x int?]] [:map [:x int?]])))
  (is (false? (mu/equals [:map [:x {} int?]] [:map [:x int?]]))))

(deftest simplify-map-entry-test
  (are [entry expected]
    (is (= expected (mu/simplify-map-entry entry)))

    [:x 'int?] [:x 'int?]
    [:x nil 'int?] [:x 'int?]
    [:x {} 'int?] [:x 'int?]
    [:x {:optional false} 'int?] [:x 'int?]
    [:x {:optional false, :x 1} 'int?] [:x {:x 1} 'int?]
    [:x {:optional true} 'int?] [:x {:optional true} 'int?]))

(deftest merge-test
  (are [?s1 ?s2 expected]
    (= true (mu/equals expected (mu/merge ?s1 ?s2)))

    int? int? int?
    int? pos-int? pos-int?
    int? nil int?
    nil pos-int? pos-int?

    [:map [:x int?]]
    [:map [:x {:optional true} pos-int?]]
    [:map [:x {:optional true} pos-int?]]

    [:map [:x {:optional true} int?]]
    [:map [:x pos-int?]]
    [:map [:x pos-int?]]

    [:map [:x {:optional false} int?]]
    [:map [:x {:optional true} pos-int?]]
    [:map [:x {:optional true} pos-int?]]

    [:map {:title "parameters"}
     [:parameters
      [:map
       [:query-params {:title "query1", :description "first"}
        [:map [:x int?]]]]]]
    [:map {:description "description"}
     [:parameters
      [:map
       [:query-params {:title "query2", :summary "second"}
        [:map [:x string?] [:y int?]]]
       [:body-params
        [:map [:z int?]]]]]]
    [:map {:title "parameters", :description "description"}
     [:parameters
      [:map
       [:query-params {:title "query2", :description "first", :summary "second"}
        [:map [:x string?] [:y int?]]]
       [:body-params
        [:map [:z int?]]]]]]))

(deftest union-test
  (are [?s1 ?s2 expected]
    (= true (mu/equals expected (mu/union ?s1 ?s2)))

    int? int? int?
    int? pos-int? [:or int? pos-int?]
    int? nil int?
    nil pos-int? pos-int?

    [:map [:x int?]]
    [:map [:x {:optional true} pos-int?]]
    [:map [:x {:optional true} [:or int? pos-int?]]]

    [:map [:x int?]]
    [:map [:x {:optional true} pos-int?]]
    [:map [:x {:optional true} [:or int? pos-int?]]]

    [:map [:x {:optional true} int?]]
    [:map [:x {:optional true} pos-int?]]
    [:map [:x {:optional true} [:or int? pos-int?]]]

    [:map [:x {:optional false} int?]]
    [:map [:x {:optional true} pos-int?]]
    [:map [:x {:optional true} [:or int? pos-int?]]]

    [:map {:title "parameters"}
     [:parameters
      [:map
       [:query-params {:title "query1", :description "first"}
        [:map [:x int?]]]]]]
    [:map {:description "description"}
     [:parameters
      [:map
       [:query-params {:title "query2", :summary "second"}
        [:map [:x string?] [:y int?]]]
       [:body-params
        [:map [:z int?]]]]]]
    [:map {:title "parameters", :description "description"}
     [:parameters
      [:map
       [:query-params {:title "query2", :description "first", :summary "second"}
        [:map [:x [:or int? string?]] [:y int?]]]
       [:body-params
        [:map [:z int?]]]]]]))

(deftest update-properties-test
  (let [schema [:and {:x 0} int?]]
    (is (mu/equals [:and {:x 1} int?]
                   (mu/update-properties schema update :x inc)))
    (is (mu/equals [:and {:x 0, :joulu "loma"} int?]
                   (mu/update-properties schema assoc :joulu "loma")))))

(deftest open-closed-schema-test
  (let [open [:map {:title "map"}
              [:a int?]
              [:b {:optional true} int?]
              [:c [:map
                   [:d int?]]]]
        closed [:map {:title "map", :closed true}
                [:a int?]
                [:b {:optional true} int?]
                [:c [:map {:closed true}
                     [:d int?]]]]]

    (is (mu/equals closed (mu/closed-schema open)))
    (is (mu/equals open (mu/open-schema closed))))

  (testing "explicitely open maps not effected"
    (let [schema [:map {:title "map", :closed false}
                  [:a int?]
                  [:b {:optional true} int?]
                  [:c [:map {, :closed false}
                       [:d int?]]]]]

      (is (mu/equals schema (mu/closed-schema schema)))
      (is (mu/equals schema (mu/open-schema schema))))))

(deftest select-key-test
  (let [schema [:map {:title "map"}
                [:a int?]
                [:b {:optional true} int?]
                [:c string?]]]
    (is (mu/equals (mu/select-keys schema []) [:map {:title "map"}]))
    (is (mu/equals (mu/select-keys schema nil) [:map {:title "map"}]))
    (is (mu/equals (mu/select-keys schema [:a]) [:map {:title "map"} [:a int?]]))
    (is (mu/equals (mu/select-keys schema #{:a}) [:map {:title "map"} [:a int?]]))
    (is (mu/equals (mu/select-keys schema '(:a)) [:map {:title "map"} [:a int?]]))
    (is (mu/equals (mu/select-keys schema [:a :b :c]) schema))
    (is (mu/equals (mu/select-keys schema [:a :b :extra])
                   [:map {:title "map"}
                    [:a int?]
                    [:b {:optional true} int?]]))))

(deftest get-test
  (is (mu/equals (mu/get [:map [:x int?]] :x) int?))
  (is (mu/equals (mu/get [:map [:x {:optional true} int?]] :x) int?))
  (is (mu/equals (mu/get [:vector int?] 0) int?))
  (is (mu/equals (mu/get [:list int?] 0) int?))
  (is (mu/equals (mu/get [:set int?] 0) int?))
  (is (mu/equals (mu/get [:sequential int?] 0) int?))
  (is (mu/equals (mu/get [:tuple int? pos-int?] 1) pos-int?))
  (is (= (mu/get [:map [:x int?]] :y)
         (mu/get [:vector int?] 1))))

(deftest get-in-test
  (is (mu/equals (mu/get-in
                   [:map
                    [:x [:vector
                         [:list
                          [:set
                           [:sequential
                            [:tuple int? [:map [:y [:maybe boolean?]]]]]]]]]]
                   [:x 0 0 0 0 1 :y 0])
                 boolean?)))

(deftest dissoc-test
  (let [schema [:map {:title "map"}
                [:a int?]
                [:b {:optional true} int?]
                [:c string?]]]
    (is (mu/equals (mu/dissoc schema :a)
                   [:map {:title "map"}
                    [:b {:optional true} int?]
                    [:c string?]]))
    (is (mu/equals (mu/dissoc schema :b)
                   [:map {:title "map"}
                    [:a int?]
                    [:c string?]]))))

(deftest assoc-test
  (let [schema [:map {:title "map"}
                [:a int?]
                [:b {:optional true} int?]
                [:c string?]]]
    (is (mu/equals (mu/assoc schema :a string?)
                   [:map {:title "map"}
                    [:a string?]
                    [:b {:optional true} int?]
                    [:c string?]]))
    (is (mu/equals (mu/assoc schema [:a {:optional true}] string?)
                   [:map {:title "map"}
                    [:a {:optional true} string?]
                    [:b {:optional true} int?]
                    [:c string?]]))
    (is (mu/equals (mu/assoc schema :b string?)
                   [:map {:title "map"}
                    [:a int?]
                    [:b string?]
                    [:c string?]]))))
