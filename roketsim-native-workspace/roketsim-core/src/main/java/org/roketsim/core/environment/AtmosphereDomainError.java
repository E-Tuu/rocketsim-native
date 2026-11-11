package org.roketsim.core.environment;

/** Finite geopotential yükseklik model domain'i dışındadır; fallback kararı vermez. */
public final class AtmosphereDomainError extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    /** Reddedilen geopotential yükseklik; serialization verisi. */
    private final double requestedGeopotentialHeightM;
    /** @param requestedGeopotentialHeightM istenen geopotential yükseklik (m) */
    public AtmosphereDomainError(double requestedGeopotentialHeightM) {
        super("U.S. Standard Atmosphere 1976 lower atmosphere: requested geopotential_height_m="
                + requestedGeopotentialHeightM + "; supported domain ["
                + USStandardAtmosphere1976Lower.USSA76_LOWER_MIN_GEOPOTENTIAL_HEIGHT_M + ", "
                + USStandardAtmosphere1976Lower.USSA76_LOWER_MAX_GEOPOTENTIAL_HEIGHT_M + "] m");
        this.requestedGeopotentialHeightM = requestedGeopotentialHeightM;
    }
    /** @return istenen geopotential yükseklik (m) */
    public double requestedGeopotentialHeightM() { return requestedGeopotentialHeightM; }
    /** @return dahil alt sınır (m) */
    public double minimumGeopotentialHeightM() { return USStandardAtmosphere1976Lower.USSA76_LOWER_MIN_GEOPOTENTIAL_HEIGHT_M; }
    /** @return dahil üst sınır (m) */
    public double maximumGeopotentialHeightM() { return USStandardAtmosphere1976Lower.USSA76_LOWER_MAX_GEOPOTENTIAL_HEIGHT_M; }
}
