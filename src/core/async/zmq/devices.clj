(ns ^{:skip-wiki true}
  core.async.zmq.devices
  (:require [clojure.core.async :as async]))

(set! *warn-on-reflection* true)

(defn chan-proxy
  ([frontend backend]
   (chan-proxy frontend backend nil))
  ([frontend backend capture]
   (letfn [(pipe [in out capture]
                  (let [data (async/<! in)]
                    (when capture
                      (async/>! capture data))
                    (async/>! out data)))]
     (async/go-loop
      []
      (async/alt!
       frontend ([msg] (pipe frontend backend capture))
       backend ([msg] (pipe backend frontend capture)))
      (recur)))))