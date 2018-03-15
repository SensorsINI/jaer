/*
 * Copyright (C) 2018 minliu.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA

https://github.com/optimatika/ojAlgo.wiki.git
The linear algebra part of ojAlgo is one of its main attractions as well as an essential component to the other parts. It's not difficult to use at all, but to fully exploit its capabilities there are a few things you need to know.

The BasicMatrix interface and its 3 implementations, BigMatrix, ComplexMatrix and PrimitiveMatrix, is what most new users find when they're looking for "ojAlgo's matrix class", but there are actually two matrix implementation layers in ojAlgo. There's the BasicMatrix interface and its implementations as well as the MatrixStore/PhysicalStore family of interfaces and classes.

BasicMatrix is a higher/application/logic level interface with a fixed and limited feature set, and MatrixStore/PhysicalStore are lower/algorithm/implementation level interfaces offering greater flexibility and control. Initially BasicMatrix was "the" interface to use. Everything else was just implementation stuff preferably hidden to users. This has changed. The lower level stuff is since long open and available to use for anyone. It is also where most of the development has been lately.

The BasicMatrix interface is designed for immutable implementations, and the BigMatrix, ComplexMatrix and PrimitiveMatrix implementations are indeed immutable. This can be very practical, but is an unusual feature for mathematical matrix classes, and most likely not what you expected. One of the things new users tend to get wrong is how to instantiate, and fully populate, an immutable matrix.

Each of the two implementation layers support three element types: double, BigDecimal and ComplexNumber. Most people will just use the double implementations, but some need ComplexNumber. If the matrices are not too large and you need that extra precision you can use BigDecimal.

The two layers are to some extent interoperable, but most users should choose either or. Have a look at both PrimitiveMatrix and PrimitiveDenseStore (assuming you need primitive double elements) and try to get some idea about the differences before you write too much code.

Example code
Below is some code demonstrating how to do some basic stuff, as well as pointing out some differences between PrimitiveMatrix and PrimitiveDenseStore.

Min Liu changed some code to make it work on the recent version (44.0).
*/

package ch.unizh.ini.jaer.projects.minliu;

import org.ojalgo.OjAlgoUtils;
import org.ojalgo.matrix.BasicMatrix;
import org.ojalgo.matrix.BasicMatrix.Builder;
import org.ojalgo.matrix.PrimitiveMatrix;
import org.ojalgo.matrix.decomposition.QR;
import org.ojalgo.matrix.store.ElementsSupplier;
import org.ojalgo.matrix.store.MatrixStore;
import org.ojalgo.matrix.store.PhysicalStore;
import org.ojalgo.matrix.store.PrimitiveDenseStore;
import org.ojalgo.matrix.task.InverterTask;
import org.ojalgo.matrix.task.SolverTask;
import org.ojalgo.RecoverableCondition;
import org.ojalgo.netio.BasicLogger;
import org.ojalgo.random.Weibull;

public class OjAlgo_example {

    public static void main(final String[] args) {

        BasicLogger.debug();
        BasicLogger.debug(OjAlgo_example.class.getSimpleName());
        BasicLogger.debug(OjAlgoUtils.getTitle());
        BasicLogger.debug(OjAlgoUtils.getDate());
        BasicLogger.debug();

        final BasicMatrix.Factory<PrimitiveMatrix> mtrxFactory = PrimitiveMatrix.FACTORY;
        final PhysicalStore.Factory<Double, PrimitiveDenseStore> storeFactory = PrimitiveDenseStore.FACTORY;
        // BasicMatrix.Factory and PhysicalStore.Factory are very similar.
        // Every factory in ojAlgo that makes 2D-structures extends/implements the same interface.

        final PrimitiveMatrix mtrxA = mtrxFactory.makeEye(5, 5);
        // Internally this creates an "eye-structure" - not a large array.
        final PrimitiveDenseStore storeA = storeFactory.makeEye(5, 5);
        // A PrimitiveDenseStore is always a "full array". No smart data structures here.

        final PrimitiveMatrix mtrxB = mtrxFactory.makeFilled(5, 3, new Weibull(5.0, 2.0));
        final PrimitiveDenseStore storeB = storeFactory.makeFilled(5, 3, new Weibull(5.0, 2.0));
        // When you create a matrix with random elements you can specify their distribution.

        /***********************************************************************
         * Matrix multiplication
         */

        final PrimitiveMatrix mtrxC = mtrxA.multiply(mtrxB);
        // Multiplying two PrimitiveMatrix:s is trivial. There are no alternatives,
        // and the returned product is a PrimitiveMatrix (same as the inputs).

        // Doing the same thing using PrimitiveDenseStore (MatrixStore) you have options...

        BasicLogger.debug("Different ways to do matrix multiplication with MatrixStore:s");
        BasicLogger.debug();

        final MatrixStore<Double> storeC = storeA.multiply(storeB);
        // One option is to do exactly what you did with PrimitiveMatrix.
        // The only difference is that the return type is MatrixStore rather than
        // PhysicalStore, PrimitiveDenseStore or whatever else you input.
        BasicLogger.debug("MatrixStore MatrixStore#multiply(MatrixStore)", storeC);

        final PrimitiveDenseStore storeCpreallocated = storeFactory.makeZero(5, 3);
        // Another option is to first create the matrix that should hold the resulting product,
        storeA.multiply(storeB, storeCpreallocated);
        // and then perform the multiplication. This enables reusing memory (the product matrix).
        BasicLogger.debug("void MatrixStore#multiply(Access1D, ElementsConsumer)", storeCpreallocated);

        final ElementsSupplier<Double> storeCsupplier = storeB.premultiply(storeA);
        // A third option is the premultiply method:
        // 1) The left and right argument matrices are interchanged.
        // 2) The return type is an ElementsSupplier rather than a MatrixStore.
        // This is because the multiplication is not yet performed.
        // It is possible to define additional operation on an ElementsSupplier.
        final MatrixStore<Double> storeClater = storeCsupplier.get();
        // The multiplication, and whatever additional operations you defined,
        // is performed when you call #get().
        BasicLogger.debug("ElementsSupplier MatrixStore#premultiply(Access1D)", storeClater);

        // A couple of more alternatives that will do the same thing.
        storeCpreallocated.fillByMultiplying(storeA, storeB);
        BasicLogger.debug("void ElementsConsumer#fillByMultiplying(Access1D, Access1D)", storeClater);
        storeCsupplier.supplyTo(storeCpreallocated);
        BasicLogger.debug("void ElementsSupplier#supplyTo(ElementsConsumer)", storeClater);

        /***********************************************************************
         * Inverting a matrix
         */

        final PrimitiveMatrix mtrxI = mtrxA.invert();
        // With PrimitiveMatrix (BasicMatrix) it is trivial, with only one option.
        // That method will do its best to always return something - possibly a
        // generalized inverse.

        // With MatrixStore:s you need to use an InverterTask
        final InverterTask<Double> tmpInverter = InverterTask.PRIMITIVE.make(storeA);
        // There are many implementations of that interface. This factory method
        // will return one that may be suitable, but most likely you will want to
        // choose implementaion based on what you know about the matrix.
        try {
            final MatrixStore<Double> storeI = tmpInverter.invert(storeA);
        } catch (final RecoverableCondition exception) {
            // Will throw and exception if inversion fails
        }

        /***********************************************************************
         * Solving an equation system
         */

        // Same same, but different, as inverting

        final PrimitiveMatrix mtrxX = mtrxA.solve(mtrxC);

        final SolverTask<Double> tmpSolver = SolverTask.PRIMITIVE.make(storeA, storeC);
        MatrixStore<Double> storeX;
        try {
            storeX = tmpSolver.solve(storeA, storeC);
        } catch (final RecoverableCondition exception) {
            // Will throw and exception if solving fails
        }

        // Most likely you want to do is to instantiate some matrix decomposition (there are several)

        final QR<Double> tmpQR = QR.PRIMITIVE.make(storeA);
        tmpQR.decompose(storeA);
        if (tmpQR.isSolvable()) {
            storeX = tmpQR.getSolution(storeC);
        } else {
            // You should verify that the equation system is solvable,
            // and do something else if it is not.
        }

        /***********************************************************************
         * Setting individual elements
         */

        storeA.set(3, 1, 3.14);
        storeA.set(3, 0, 2.18);
        // PhysicalStore instances are naturally mutable.
        // If you want to set or modify something - just do it

        final Builder<PrimitiveMatrix> mtrxBuilder = mtrxA.copy();
        // PrimitiveMatrix is immutable. To modify anything you need to copy it to Builder instance.

        mtrxBuilder.add(3, 1, 3.14);
        mtrxBuilder.add(3, 0, 2.18);

        final PrimitiveMatrix mtrxM = mtrxBuilder.build();

        /***********************************************************************
         * Creating matrices by explicitly setting all elements
         */

        final double[][] tmpData = new double[][] { { 1.0, 2.0, 3.0 }, { 4.0, 5.0, 6.0 }, { 7.0, 8.0, 9.0 } };

        final PrimitiveMatrix mtrxR = mtrxFactory.rows(tmpData);
        final PrimitiveDenseStore storeR = storeFactory.rows(tmpData);
        // There more different #rows(...) methods, each with a corresponding #columns(...) method.

        // If you don't want/need to first create some (intermediate) array to for
        // the elements, you can of course set them on the matrix directly.
        final PrimitiveDenseStore storeZ = storeFactory.makeEye(3, 3);
        // Since PrimitiveMatrix is immutable this has to be done via a builder.
        final Builder<PrimitiveMatrix> mtrxZbuilder = mtrxFactory.getBuilder(3, 3);

        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 3; i++) {
                mtrxZbuilder.set(i, j, i * j);
                storeZ.set(i, j, i * j);
            }
        }

        final PrimitiveMatrix mtrxZ = mtrxZbuilder.get();

    }
}
