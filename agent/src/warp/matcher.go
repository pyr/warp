package warp

type Clause interface {
	Validate(Environment) bool
}

type MatcherType string

const (
	MATCHER_ALL  string = "all"
	MATCHER_NONE string = "none"
	MATCHER_NOT  string = "not"
	MATCHER_OR  string = "or"
	MATCHER_AND string = "and"
	MATCHER_HOST  string = "host"
	MATCHER_FACT string = "fact"
)

type MatcherDescription struct {
	Type MatcherType             `json:"type" binding:"required"`
	Clause *MatcherDescription    `json:"clause,omitempty"`
	Clauses []*MatcherDescription `json:"clauses,omitempty"`
	FactKey string               `json:"fact,omitempty"`
	FactValue string             `json:"value,omitempty"`
	Host string                  `json:"host,omitempty"`
}

type AllMatcher struct {
}

type NoneMatcher struct {
}

type AndMatcher struct {
	Clauses []Clause
}

type OrMatcher struct {
	Clauses []Clause
}

type NotMatcher struct {
	NotClause Clause
}

type HostMatcher struct {
	Host string
}

type FactMatcher struct {
	FactKey string
	FactValue string
}

func (m AllMatcher) Validate(env Environment) bool {
	return true
}

func (m NoneMatcher) Validate(env Environment) bool {
	return false
}

func (m AndMatcher) Validate(env Environment) bool {

	for c := range m.Clauses {
		if !m.Clauses[c].Validate(env) {
			return false
		}
	}
	return true
}

func (m OrMatcher) Validate(env Environment) bool {
	for c := range m.Clauses {
		if m.Clauses[c].Validate(env) {
			return true
		}
	}
	return false
}

func (m NotMatcher) Validate(env Environment) bool {
	return !m.NotClause.Validate(env)
}

func (m HostMatcher) Validate(env Environment) bool {
	if env.Host() == m.Host {
		return true
	}
	return false
}

func (m FactMatcher) Validate(env Environment) bool {
	if env.Lookup(m.FactKey) == m.FactValue {
		return true
	}
	return false

}

func (md MatcherDescription) DescriptionToMatcher() Clause {
	switch {
	case md.Type == "all":
		return AllMatcher{}
	case md.Type == "none":
		return NoneMatcher{}
	case md.Type == "host":
		return HostMatcher{Host: md.Host}
	case md.Type == "fact":
		return FactMatcher{FactKey: md.FactKey, FactValue: md.FactValue}
	case md.Type == "not":
		return NotMatcher{NotClause: md.Clause.DescriptionToMatcher()}
	case md.Type == "or":
		clauses := make([]Clause, 0)
		for _, desc := range(md.Clauses) {
			clauses = append(clauses, desc.DescriptionToMatcher())
		}
		return OrMatcher{Clauses: clauses}
	case md.Type == "and":
		clauses := make([]Clause, 0)
		for _, desc := range(md.Clauses) {
			clauses = append(clauses, desc.DescriptionToMatcher())
		}
		return AndMatcher{Clauses: clauses}
	}
	return NoneMatcher{}
}
