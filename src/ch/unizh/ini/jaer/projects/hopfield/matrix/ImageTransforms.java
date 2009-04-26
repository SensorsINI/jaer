package ch.unizh.ini.jaer.projects.hopfield.matrix;

public class ImageTransforms {
	private static int[][] getHadamard(int N){
				int[][] H = new int[N][N];

			      // initialize Hadamard matrix of order N
			      H[0][0] = 1;
			      for (int n = 1; n < N; n += n) {
			         for (int i = 0; i < n; i++) {
			            for (int j = 0; j < n; j++) {
			               H[i+n][j]   =  H[i][j];
			               H[i][j+n]   =  H[i][j];
			               H[i+n][j+n] = 0-H[i][j];
			            }
			         }
			      }

			      return H;
	}
	
	private static int[][] getMatrix(int N, int mode){
		int[][] H = new int[N][N];
		switch(mode){
		case(1)://vertical black left
			     for (int i = 0; i < N; i++) {
		            for (int j = 0; j < N; j++) {
		            	if(i<N/2){
		            		H[i][j] = -1;
		            		
		            	}
		            	else{
		            		H[i][j] = 1;
		            	}
		            }
			     }
		      
			break;
		case(2)://vertical black right
			for (int i = 0; i < N; i++) {
	            for (int j = 0; j < N; j++) {
	            	if(i<N/2){
	            		H[i][j] = 1;
	            		
	            	}
	            	else{
	            		H[i][j] = -1;
	            	}
	            }
		     }
	      
			break;
		case(3)://horizontal black top
			for (int i = 0; i < N; i++) {
	            for (int j = 0; j < N; j++) {
	            	if(j<N/2){
	            		H[i][j] = -1;
	            		
	            	}
	            	else{
	            		H[i][j] = 1;
	            	}
	            }
		     }
	      
		
			break;
		case(4)://horizontal black bottom
			for (int i = 0; i < N; i++) {
	            for (int j = 0; j < N; j++) {
	            	if(j<N/2){
	            		H[i][j] = 1;
	            		
	            	}
	            	else{
	            		H[i][j] = -1;
	            	}
	            }
		     }
	      
			break;
		default:
			break;
		}
	      // initialize Hadamard matrix of order N
	      

	      return H;
}
	

	
	
	public static IntMatrix calculate1DHadamard(IntMatrix pattern){
		int[][] hadamard = getHadamard(pattern.size());
		IntMatrix hadamardMatrix = new IntMatrix(hadamard);
		return MatrixMath.multiply(hadamardMatrix, pattern);
	}
	
	public static IntMatrix calculate2DHadamard(IntMatrix pattern){
		int[][] hadamard = getHadamard(pattern.getCols());
		IntMatrix hadamardMatrix = new IntMatrix(hadamard);
		return MatrixMath.multiply(MatrixMath.multiply(hadamardMatrix, pattern),hadamardMatrix);
	}
}
