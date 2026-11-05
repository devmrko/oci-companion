package main
import ("encoding/json"; "net/http"; "time"; "log")
func handler(w http.ResponseWriter, r *http.Request) {
 w.Header().Set("Content-Type", "application/json")
 if r.Method != "GET" { w.WriteHeader(405); json.NewEncoder(w).Encode(map[string]string{"error":"method not allowed"}); return }
 if r.URL.Path != "/hello" { w.WriteHeader(404); json.NewEncoder(w).Encode(map[string]string{"error":"not found"}); return }
 json.NewEncoder(w).Encode(map[string]string{"message":"hello","language":"go"})
}
func main() { s:=&http.Server{Addr:":8080",Handler:http.HandlerFunc(handler),ReadHeaderTimeout:5*time.Second};log.Fatal(s.ListenAndServe()) }
