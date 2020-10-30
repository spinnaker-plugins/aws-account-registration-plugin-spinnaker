package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"
)

func hello(w http.ResponseWriter, req *http.Request) {
	lt := req.URL.Query().Get("UpdatedAt.gt")
	var rAccs []Account
	if lt != "" {
		lts := strings.Replace(lt, " ", "+", 1)
		givenTime, err := time.Parse(time.RFC3339Nano, lts)
		if err != nil {
			fmt.Println(err)
			return
		}
		accountsToReturn, mostRecentTime := loadJSON()
		if givenTime.Before(mostRecentTime) {
			rAccs = append(rAccs, accountsToReturn.Accounts...)
		}
	} else {
		currentAcounts, _ := loadJSON()
		rAccs = append(rAccs, currentAcounts.Accounts...)
	}

	resp := Response{
		Accounts: rAccs,
	}
	fmt.Println(resp)
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(resp)
}

func loadJSON() (Response, time.Time) {
	var res Response
	file, err := os.Open("response.json")
	if err != nil {
		fmt.Println("Err opening file")
		return res, time.Time{}
	}
	defer file.Close()

	dec := json.NewDecoder(file)
	err = dec.Decode(&res)
	if err != nil {
		fmt.Println("Error decoding")
		return res, time.Time{}
	}
	var mostRecent time.Time
	for i, v := range res.Accounts {
		timeInt, err := strconv.ParseInt(v.UpdatedAt, 10, 64)
		if err != nil {
			fmt.Printf("error parsing time stamp: %s", v.UpdatedAt)
			return Response{}, time.Time{}
		}
		unixTime := time.Unix(0, timeInt).UTC()
		rfcTime := unixTime.Format(time.RFC3339Nano)
		res.Accounts[i].UpdatedAt = rfcTime
		if unixTime.After(mostRecent) {
			mostRecent = unixTime
		}
	}
	return res, mostRecent
}

func main() {
	http.HandleFunc("/hello", hello)
	http.HandleFunc("/hello/", hello)
	http.ListenAndServe(":8080", nil)
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