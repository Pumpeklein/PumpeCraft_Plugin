package de.pumpecraft.bases.plot;

import de.pumpecraft.bases.PlotSettings;

/**
 * Was ein Grundstück kostet. Die Fläche gibt den Grundpreis, die Lage einen Faktor darauf: Am
 * Ursprung 0/0 zahlt man voll, mit wachsendem Abstand gleichmäßig weniger, bis zu einem
 * Mindestfaktor. So bleibt Bauland am Spawn knapp, ohne die Ferne zu verschenken.
 */
public final class PlotPricing {
    private final PlotSettings settings;

    public PlotPricing(PlotSettings settings) {
        this.settings = settings;
    }

    public Quote quote(PlotArea area) {
        double distance = area.distanceFromOrigin();
        double factor = factorAt(distance);
        long base = settings.pricePerBlock() * area.area();
        long price = Math.max(settings.minimumPrice(), Math.round(base * factor));
        return new Quote(area.area(), base, distance, factor, price);
    }

    public long refundFor(Plot plot) {
        return Math.max(0L, Math.round(plot.pricePaid() * settings.refundPercent() / 100.0D));
    }

    public double factorAt(double distance) {
        double full = settings.fullPriceRadius();
        double cheapest = settings.cheapestAt();
        double minimum = settings.minimumFactor();
        if (distance <= full) {
            return 1.0D;
        }
        if (distance >= cheapest || cheapest <= full) {
            return minimum;
        }
        return 1.0D - (1.0D - minimum) * (distance - full) / (cheapest - full);
    }

    public record Quote(long blocks, long basePrice, double distance, double factor, long price) {
    }
}
