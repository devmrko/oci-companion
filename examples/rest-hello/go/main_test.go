package main
import ("net/http/httptest"; "testing"; "encoding/json")
func TestHello(t *testing.T) { r:=httptest.NewRequest("GET","/hello",nil);w:=httptest.NewRecorder();handler(w,r);var v map[string]string;if err:=json.Unmarshal(w.Body.Bytes(),&v);err!=nil || w.Code!=200 || v["message"]!="hello" {t.Fatal(w.Body.String())} }
