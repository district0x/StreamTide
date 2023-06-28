FROM 487920318758.dkr.ecr.us-west-2.amazonaws.com/cljs-web3-ci:node-18.7.0 AS build_stage
USER root

ARG BUILD_ENV=qa
ENV BUILD_ENV=${BUILD_ENV}
ENV STREAMTIDE_ENV=${BUILD_ENV}


COPY  . /build/
WORKDIR /build

RUN yarn install && \
    bb compile-css && \
    bb release-ui && \
    truffle compile


FROM nginx:alpine
ENV BUILD_ENV=${BUILD_ENV}
ENV STREAMTIDE_ENV=${BUILD_ENV}

COPY docker-builds/ui/streamtide.conf /etc/nginx/conf.d/
COPY docker-builds/ui/default.conf /etc/nginx/conf.d/default.conf

COPY --from=build_stage  /build/resources/public /streamtide/resources/public/

EXPOSE 80