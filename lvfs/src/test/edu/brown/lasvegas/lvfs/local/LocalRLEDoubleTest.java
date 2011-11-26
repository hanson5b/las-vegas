package edu.brown.lasvegas.lvfs.local;

import edu.brown.lasvegas.lvfs.AllValueTraits;
import edu.brown.lasvegas.lvfs.FixLenValueTraits;

public class LocalRLEDoubleTest extends LocalRLETestBase<Double, double[]> {
    @Override
    protected Double generateValue(int index) { return (923482.87453457 * (index / 5)) - (Math.pow(index, 1.47d));  }
    @Override
    protected FixLenValueTraits<Double, double[]> createTraits() { return new AllValueTraits.DoubleValueTraits();}
    @Override
    protected double[] createArray (int size) { return new double[size];}
    @Override
    protected void setToArray(double[] array, int index, Double value) { array[index] = value; }
    @Override
    protected Double getFromArray(double[] array, int index) { return array[index]; }
}