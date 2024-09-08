FROM 487920318758.dkr.ecr.us-west-2.amazonaws.com/cljs-web3-ci:node-18.17.0
USER root

ARG BUILD_ENV=qa
ENV BUILD_ENV=${BUILD_ENV}
ENV STREAMTIDE_ENV=${BUILD_ENV}


COPY  . /build/
WORKDIR /build

RUN yarn install && \
bb release-server && \
bb compile-contracts

EXPOSE 6300 3000
ENTRYPOINT ["node", "server/streamtide_server.js"]
