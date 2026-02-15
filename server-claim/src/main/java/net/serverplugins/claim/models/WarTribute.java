package net.serverplugins.claim.models;

import java.time.Instant;

public class WarTribute {

    public enum OfferingSide {
        ATTACKER,
        DEFENDER
    }

    public enum TributeType {
        SURRENDER("Surrender", "Unconditional surrender to end the war"),
        PEACE_OFFER("Peace Offer", "Offer peace terms with compensation"),
        TRIBUTE_DEMAND("Tribute Demand", "Demand tribute to end hostilities");

        private final String displayName;
        private final String description;

        TributeType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        public static TributeType fromString(String name) {
            try {
                return TributeType.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                return PEACE_OFFER;
            }
        }
    }

    public enum Response {
        PENDING("Pending"),
        ACCEPTED("Accepted"),
        REJECTED("Rejected");

        private final String displayName;

        Response(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static Response fromString(String name) {
            try {
                return Response.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                return PENDING;
            }
        }
    }

    private int id;
    private int warId;
    private OfferingSide offeringSide;
    private TributeType tributeType;
    private double moneyAmount;
    private String message;
    private Response response;
    private Instant createdAt;
    private Instant respondedAt;

    public WarTribute() {
        this.response = Response.PENDING;
        this.createdAt = Instant.now();
    }

    public WarTribute(
            int warId,
            OfferingSide offeringSide,
            TributeType tributeType,
            double moneyAmount,
            String message) {
        this.warId = warId;
        this.offeringSide = offeringSide;
        this.tributeType = tributeType;
        this.moneyAmount = moneyAmount;
        this.message = message;
        this.response = Response.PENDING;
        this.createdAt = Instant.now();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getWarId() {
        return warId;
    }

    public void setWarId(int warId) {
        this.warId = warId;
    }

    public OfferingSide getOfferingSide() {
        return offeringSide;
    }

    public void setOfferingSide(OfferingSide offeringSide) {
        this.offeringSide = offeringSide;
    }

    public TributeType getTributeType() {
        return tributeType;
    }

    public void setTributeType(TributeType tributeType) {
        this.tributeType = tributeType;
    }

    public double getMoneyAmount() {
        return moneyAmount;
    }

    public void setMoneyAmount(double moneyAmount) {
        this.moneyAmount = moneyAmount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Response getResponse() {
        return response;
    }

    public void setResponse(Response response) {
        this.response = response;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Instant respondedAt) {
        this.respondedAt = respondedAt;
    }

    public boolean isPending() {
        return response == Response.PENDING;
    }

    public boolean isAccepted() {
        return response == Response.ACCEPTED;
    }

    public boolean isRejected() {
        return response == Response.REJECTED;
    }

    public void accept() {
        this.response = Response.ACCEPTED;
        this.respondedAt = Instant.now();
    }

    public void reject() {
        this.response = Response.REJECTED;
        this.respondedAt = Instant.now();
    }

    public boolean isSurrender() {
        return tributeType == TributeType.SURRENDER;
    }

    public boolean isPeaceOffer() {
        return tributeType == TributeType.PEACE_OFFER;
    }

    public boolean isTributeDemand() {
        return tributeType == TributeType.TRIBUTE_DEMAND;
    }
}
