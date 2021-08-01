#!/bin/bash -e
mkdir -p classes
clj -M:clj -e "(compile 'ajanottaja.core)"
cd "$( dirname "${BASH_SOURCE[0]}" )"
clojure -Aclj -M -m uberdeps.uberjar --main-class ajanottaja.core --deps-file ../deps.edn --aliases clj --target ../target/ajanottaja.jar