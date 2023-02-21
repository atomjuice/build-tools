FROM babashka/babashka

COPY . /

ENTRYPOINT ["bb"]
