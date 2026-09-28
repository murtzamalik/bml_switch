# OpenAPI examples — AssanPay nests (Phase 0)

Live docs: `http://localhost:8080/swagger-ui.html` after `docker compose up`.

## Balance — capital `Return`

```json
{
  "Return": {
    "Amount": "150000.00",
    "BranchCode": "001",
    "FromAccount": "0345001234567",
    "CURRENCY_CODE": "PKR"
  },
  "Response_Code": "00",
  "Response_Desc": "Success"
}
```

## IFT — nested lowercase `return`

```json
{
  "return": {
    "fromAccount": "0345001234567",
    "toAccount": "0345001234568",
    "amount": "100.00",
    "stan": "123456",
    "status": "00",
    "statusDescription": "Success",
    "PurposeOfPayment": "FAM"
  },
  "Response_Code": "00",
  "Response_Desc": "Success"
}
```

## IBFT payment — flat

```json
{
  "Response_Code": "00",
  "Response_Desc": "Success",
  "stan": "654321",
  "transactionID": "TXN-ABCDEF123456",
  "fee": "0.00"
}
```

## IBFT title — double nest

```json
{
  "IBFTTitleFetchResponse": {
    "return": {
      "accountTitle": "ALI KHAN",
      "toIBAN": "PK12MEZN0000001234567890",
      "toIMD": "601004",
      "toAccount": "1234567890"
    }
  },
  "Response_Code": "00",
  "Response_Desc": "Success"
}
```

Mock OTP code: **`1234`** (`MOCK_OTP=true`).
