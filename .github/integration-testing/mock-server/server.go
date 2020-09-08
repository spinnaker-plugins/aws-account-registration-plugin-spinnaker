package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"strconv"
	"time"
)

var accounts = make(map[string]Account)

func hello(w http.ResponseWriter, req *http.Request) {
	lt := req.URL.Query().Get("UpdatedAt.gt")
	var rAccs []Account
	if lt != "" {
		givenTime, err := time.Parse(time.RFC3339Nano, lt)
		if err != nil {
			fmt.Println(err)
		}
		if modified(givenTime) {
			newAccounts := loadJSON()
			for _, v := range newAccounts.Accounts {
				accounts[v.AccountName] = v
				rAccs = append(rAccs, v)
			}

			//for _, a := range accounts {
			//	rAccs = append(rAccs, a)
			//}
		}
	} else {
		currentAcounts := loadJSON()
		for _, v := range currentAcounts.Accounts {
			accounts[v.AccountName] = v
			rAccs = append(rAccs, v)
		}
		//for _, v := range accounts {
		//	rAccs = append(rAccs, v)
			}

	resp := Response{
		Accounts: rAccs,
	}
	fmt.Println(resp)
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(resp)
}

func modified(t time.Time) bool {
	info, err := os.Stat("response.json")
	if err != nil {
		fmt.Println(err)
	}
	if info.ModTime().UTC().After(t) {
		return true
	}
	return false
}

func loadJSON() Response {
	var res Response
	file, err := os.Open("response.json")
	if err != nil {
		fmt.Println("Err opening file")
		return res
	}
	defer file.Close()

	dec := json.NewDecoder(file)
	err = dec.Decode(&res)
	if err != nil {
		fmt.Println("Error decoding")
		return res
	}

	for i, v := range res.Accounts {
		timeInt, err := strconv.ParseInt(v.UpdatedAt, 10, 64)
		if err != nil {
			fmt.Printf("error parsing time stamp: %s", v.UpdatedAt)
			return Response{}
		}
		unixTime := time.Unix(0, timeInt).UTC()
		rfcTime := unixTime.Format(time.RFC3339Nano)
		res.Accounts[i].UpdatedAt = rfcTime
	}
	return res
}

func main() {
	accs := loadJSON()
	for _, v := range accs.Accounts {
		accounts[v.AccountName] = v
	}
	http.HandleFunc("/hello", hello)
	http.HandleFunc("/hello/", hello)
	http.ListenAndServe(":8080", nil)
}


//type Response struct {
//	Accounts []Account `json:"Accounts"`
//	Bookmark int64                  `json:"Bookmark"` // required
//
//}


type Permissions struct {
	READ    []string `json:"READ"`
	WRITE   []string `json:"WRITE"`
	EXECUTE []string `json:"EXECUTE"`
}
type Response struct {
	Accounts   []Account `json:"SpinnakerAccounts"`
	Pagination struct {
		NextURL string `json:"NextUrl"`
	} `json:"Pagination"`
}

type Account struct {
	AccountID           string   `json:"AccountId"`
	AccountName         string   `json:"SpinnakerAccountName"`
	Regions             []string `json:"Regions"`
	SpinnakerStatus     string   `json:"SpinnakerStatus"`
	SpinnakerAssumeRole string   `json:"SpinnakerAssumeRole"`
	SpinnakerProviders  []string `json:"SpinnakerProviders"`
	CreatedAt           string   `json:"CreatedAt"`
	UpdatedAt           string   `json:"UpdatedAt"`
}