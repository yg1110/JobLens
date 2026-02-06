package com.joblens.api.jobposting.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "joblens.scoring")
public class ScoringKeywordsProperties {

    private HardFilter hardFilter = new HardFilter();
    private Map<String, List<String>> role = new HashMap<>();
    private Map<String, List<String>> employment = new HashMap<>();
    private Map<String, List<String>> experience = new HashMap<>();
    private Map<String, List<String>> stack = new HashMap<>();
    private List<String> jsp = new ArrayList<>();
    private Map<String, List<String>> domain = new HashMap<>();
    private List<String> culturePositive = new ArrayList<>();

    public HardFilter getHardFilter() {
        return hardFilter;
    }

    public void setHardFilter(HardFilter hardFilter) {
        this.hardFilter = hardFilter;
    }

    public Map<String, List<String>> getRole() {
        return role;
    }

    public void setRole(Map<String, List<String>> role) {
        this.role = role;
    }

    public Map<String, List<String>> getEmployment() {
        return employment;
    }

    public void setEmployment(Map<String, List<String>> employment) {
        this.employment = employment;
    }

    public Map<String, List<String>> getExperience() {
        return experience;
    }

    public void setExperience(Map<String, List<String>> experience) {
        this.experience = experience;
    }

    public Map<String, List<String>> getStack() {
        return stack;
    }

    public void setStack(Map<String, List<String>> stack) {
        this.stack = stack;
    }

    public List<String> getJsp() {
        return jsp;
    }

    public void setJsp(List<String> jsp) {
        this.jsp = jsp;
    }

    public Map<String, List<String>> getDomain() {
        return domain;
    }

    public void setDomain(Map<String, List<String>> domain) {
        this.domain = domain;
    }

    public List<String> getCulturePositive() {
        return culturePositive;
    }

    public void setCulturePositive(List<String> culturePositive) {
        this.culturePositive = culturePositive;
    }

    public static class HardFilter {
        private List<String> contract = new ArrayList<>();
        private List<String> nonDevRoles = new ArrayList<>();

        public List<String> getContract() {
            return contract;
        }

        public void setContract(List<String> contract) {
            this.contract = contract;
        }

        public List<String> getNonDevRoles() {
            return nonDevRoles;
        }

        public void setNonDevRoles(List<String> nonDevRoles) {
            this.nonDevRoles = nonDevRoles;
        }
    }
}

