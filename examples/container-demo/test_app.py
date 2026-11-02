from app import payload

def test_service():
    assert payload()["service"] == "oci-companion-container-demo"

def test_health():
    assert payload()["status"] == "ok"
