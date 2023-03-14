FROM ubuntu:20.04

MAINTAINER Sven Fiergolla "fiergolla@informatik.uni-trier.de"

ENV DEBIAN_FRONTEND noninteractive

RUN apt-get update && apt-get upgrade -y && apt-get install -y git make cmake g++ gcc bison flex

WORKDIR /home/git

RUN git clone https://github.com/igraph/igraph

RUN git clone https://github.com/uef-machine-learning/gclu.git

WORKDIR /home/git/igraph

RUN mkdir build && \
    cd build && \
    cmake .. && \
    cmake --build . && \
    cmake --install .

WORKDIR /home/git/gclu

RUN make gclu_ig

ENTRYPOINT ./gclu