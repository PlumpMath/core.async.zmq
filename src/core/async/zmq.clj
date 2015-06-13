(ns ^{:skip-wiki true}
  core.async.zmq
  (:require [clojure.core.async.impl.protocols :as impl])
  (:import [org.zeromq ZContext ZMQ ZMQ$Socket]))

(set! *warn-on-reflection* true)

(def ^:const transport-types
  {:inproc "inproc://"
   :ipc "ipc://"
   :tcp "tcp://"
   :pgm "pgm://"
   :epgm "epgm://"})

(def ^:const socket-types
  {:req    ZMQ/REQ
   :rep    ZMQ/REP
   :pub    ZMQ/PUB
   :sub    ZMQ/SUB
   :xpub   ZMQ/XPUB
   :xsub   ZMQ/XSUB
   :dealer ZMQ/DEALER
   :router ZMQ/ROUTER
   :pull   ZMQ/PULL
   :push   ZMQ/PUSH
   :pair   ZMQ/PAIR})

(def ^:const version
  {:major (ZMQ/getMajorVersion)
   :minor (ZMQ/getMinorVersion)
   :patch (ZMQ/getPatchVersion)})

(def ^:private ^ZContext context
  (let [context (ZContext.)]
    (.addShutdownHook (Runtime/getRuntime) (Thread. #(.close context)))
    context))

(defn- box [result]
  (reify clojure.lang.IDeref
    (deref [_] result)))

(deftype Channel
  [^ZMQ$Socket socket closed]
  impl/ReadPort
  (take!
   [_ handler]
   (when-not @closed
     (when-let [value (if (impl/blockable? handler)
                        (read-string (.recvStr socket))
                        (read-string (.recvStr socket ZMQ/NOBLOCK)))]
       (box value))))
  impl/WritePort
  (put!
   [_ message _]
   (if @closed
     (box false)
     (do
       (.send socket (if (string? message)
                       (str "\"" message "\"")
                       (str message)))
       (box true))))
  impl/Channel
  (closed? [_] @closed)
  (close! [_]
          (when-not @closed
            (reset! closed true)
            ;; REVIEW The socket will be closed when the context closes.
            ;; Explicitly closing the socket here causes the process to hang
            ;; on termination when using JeroMQ.
            ;;  (.close socket)
            )))

(defn- channel [socket]
  (Channel. socket (atom false)))

(defn chan
  [type bind-or-connect transport endpoint]
  (let [socket (.createSocket context (type socket-types))
        connection (str (transport transport-types) endpoint)]
    (case bind-or-connect
      :bind (.bind socket connection)
      :connect (.connect socket connection))
    (channel socket)))
