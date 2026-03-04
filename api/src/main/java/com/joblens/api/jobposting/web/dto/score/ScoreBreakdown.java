package com.joblens.api.jobposting.web.dto.score;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ScoreBreakdown {

    @JsonProperty("A_location")
    private ScoreComponent aLocation;

    @JsonProperty("B_employment")
    private ScoreComponent bEmployment;

    @JsonProperty("C_role_fit")
    private ScoreComponent cRoleFit;

    @JsonProperty("D_experience_fit")
    private ScoreComponent dExperienceFit;

    @JsonProperty("E_stack_fit")
    private ScoreComponent eStackFit;

    @JsonProperty("F_domain")
    private ScoreComponent fDomain;

    @JsonProperty("G_culture")
    private ScoreComponent gCulture;

    @JsonProperty("H_jd_quality")
    private ScoreComponent hJdQuality;

    public ScoreComponent getaLocation() {
        return aLocation;
    }

    public void setaLocation(ScoreComponent aLocation) {
        this.aLocation = aLocation;
    }

    public ScoreComponent getbEmployment() {
        return bEmployment;
    }

    public void setbEmployment(ScoreComponent bEmployment) {
        this.bEmployment = bEmployment;
    }

    public ScoreComponent getcRoleFit() {
        return cRoleFit;
    }

    public void setcRoleFit(ScoreComponent cRoleFit) {
        this.cRoleFit = cRoleFit;
    }

    public ScoreComponent getdExperienceFit() {
        return dExperienceFit;
    }

    public void setdExperienceFit(ScoreComponent dExperienceFit) {
        this.dExperienceFit = dExperienceFit;
    }

    public ScoreComponent geteStackFit() {
        return eStackFit;
    }

    public void seteStackFit(ScoreComponent eStackFit) {
        this.eStackFit = eStackFit;
    }

    public ScoreComponent getfDomain() {
        return fDomain;
    }

    public void setfDomain(ScoreComponent fDomain) {
        this.fDomain = fDomain;
    }

    public ScoreComponent getgCulture() {
        return gCulture;
    }

    public void setgCulture(ScoreComponent gCulture) {
        this.gCulture = gCulture;
    }

    public ScoreComponent gethJdQuality() {
        return hJdQuality;
    }

    public void sethJdQuality(ScoreComponent hJdQuality) {
        this.hJdQuality = hJdQuality;
    }
}

