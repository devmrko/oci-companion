FROM golang:1.24-alpine AS build
WORKDIR /src
COPY go.mod *.go ./
RUN go test ./... && CGO_ENABLED=0 go build -trimpath -o /hello .
FROM scratch
COPY --from=build /hello /hello
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/hello"]
