package edu.brown.lasvegas.lvfs.local;

import edu.brown.lasvegas.traits.DoubleValueTraits;
import edu.brown.lasvegas.traits.FixLenValueTraits;

public class LocalBlockCompressionFixLenDoubleTest extends LocalBlockCompressionFixLenTestBase<Double, double[]> {
    @Override
    protected Double generateValue(int index) { return (923482.87453457 * index) - (Math.pow(index, 1.47d));  }
    @Override
    protected FixLenValueTraits<Double, double[]> createTraits() { return new DoubleValueTraits();}
    @Override
    protected double[] createArray (int size) { return new double[size];}
    @Override
    protected void setToArray(double[] array, int index, Double value) { array[index] = value; }
    @Override
    protected Double getFromArray(double[] array, int index) { return array[index]; }
}
