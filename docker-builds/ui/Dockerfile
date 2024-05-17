FROM 487920318758.dkr.ecr.us-west-2.amazonaws.com/cljs-web3-ci:node-18.7.0 AS build_stage
USER root

ARG BUILD_ENV=qa
ARG FONTS_PASS
ENV BUILD_ENV=${BUILD_ENV}
ENV STREAMTIDE_ENV=${BUILD_ENV}


COPY  . /build/
WORKDIR /build

RUN yarn install && \
    bb extract-fonts ${FONTS_PASS} && \
    bb compile-css && \
    bb release-ui && \
    bb compile-contracts


FROM nginx:alpine
ENV BUILD_ENV=${BUILD_ENV}
ENV STREAMTIDE_ENV=${BUILD_ENV}
COPY docker-builds/ui/streamtide.conf /etc/nginx/conf.d/
COPY docker-builds/ui/default.conf /etc/nginx/conf.d/default.conf
COPY docker-builds/ui/nginx.conf /etc/nginx/nginx.conf

COPY --from=build_stage  /build/resources/public /streamtide/resources/public/

EXPOSE 80