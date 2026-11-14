import java.util.Arrays;
import java.util.Random;

/**
 * Демонстраційна система кодування коду-добутку 10 x 22.
 * Запуск: ProductCodeApp [імовірність_помилки] [seed]
 */
public class ProductCodeApp {
    private static final int VERTICAL_N = 10;
    private static final int VERTICAL_K = 6;
    private static final int HORIZONTAL_N = 22;
    private static final int HORIZONTAL_K = 17;

    public static void main(String[] args) {
        double errorProbability = args.length > 0
                ? Double.parseDouble(args[0]) : 0.02;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 2025L;
        if (errorProbability < 0.0 || errorProbability > 0.5) {
            throw new IllegalArgumentException("p має бути в межах [0; 0.5]");
        }

        Random random = new Random(seed);
        int[][] information = randomMatrix(VERTICAL_K, HORIZONTAL_K, random);
        int[][] encoded = encodeProduct(information);
        int[][] received = passThroughChannel(encoded, errorProbability, random);
        int errorsBefore = countDifferences(encoded, received);
        int[][] decoded = decodeProduct(received, 8);
        int errorsAfter = countDifferences(encoded, decoded);
        int[][] recoveredInformation = extractInformation(decoded);
        boolean informationRestored = Arrays.deepEquals(information, recoveredInformation);

        System.out.println("Код-добуток на основі скорочених кодів Хеммінга");
        System.out.printf("Параметри: вертикальний (%d,%d), горизонтальний (%d,%d)%n",
                VERTICAL_N, VERTICAL_K, HORIZONTAL_N, HORIZONTAL_K);
        System.out.printf("Імовірність помилки каналу p = %.4f; seed = %d%n",
                errorProbability, seed);
        System.out.println("Інформаційна матриця (6 x 17):");
        printMatrix(information);
        System.out.println("Закодована матриця (10 x 22):");
        printMatrix(encoded);
        System.out.println("Прийнята матриця після каналу:");
        printMatrix(received);
        System.out.println("Відновлена інформаційна матриця:");
        printMatrix(recoveredInformation);
        System.out.printf("Помилкових бітів до декодування: %d%n", errorsBefore);
        System.out.printf("Відмінностей від кодового слова після декодування: %d%n", errorsAfter);
        System.out.printf("Початкові дані відновлено: %s%n", informationRestored ? "так" : "ні");
        System.out.printf("Швидкість коду: %.4f%n",
                (double) (VERTICAL_K * HORIZONTAL_K) / (VERTICAL_N * HORIZONTAL_N));
    }

    private static int[][] randomMatrix(int rows, int columns, Random random) {
        int[][] result = new int[rows][columns];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) result[i][j] = random.nextInt(2);
        }
        return result;
    }

    private static int[][] encodeProduct(int[][] information) {
        int[][] verticallyEncoded = new int[VERTICAL_N][HORIZONTAL_K];
        for (int column = 0; column < HORIZONTAL_K; column++) {
            int[] message = new int[VERTICAL_K];
            for (int row = 0; row < VERTICAL_K; row++) message[row] = information[row][column];
            int[] codeword = encodeHamming(message, VERTICAL_N, VERTICAL_K);
            for (int row = 0; row < VERTICAL_N; row++) verticallyEncoded[row][column] = codeword[row];
        }

        int[][] product = new int[VERTICAL_N][HORIZONTAL_N];
        for (int row = 0; row < VERTICAL_N; row++) {
            product[row] = encodeHamming(verticallyEncoded[row], HORIZONTAL_N, HORIZONTAL_K);
        }
        return product;
    }

    private static int[] encodeHamming(int[] message, int n, int k) {
        if (message.length != k) throw new IllegalArgumentException("Неправильна довжина блока");
        int[] codeword = new int[n];
        int dataIndex = 0;
        for (int position = 1; position <= n; position++) {
            if (!isParityPosition(position)) codeword[position - 1] = message[dataIndex++];
        }
        for (int parityPosition = 1; parityPosition <= n; parityPosition <<= 1) {
            int parity = 0;
            for (int position = 1; position <= n; position++) {
                if ((position & parityPosition) != 0 && position != parityPosition) {
                    parity ^= codeword[position - 1];
                }
            }
            codeword[parityPosition - 1] = parity;
        }
        return codeword;
    }

    private static int[][] passThroughChannel(int[][] source, double p, Random random) {
        int[][] received = copy(source);
        for (int row = 0; row < source.length; row++) {
            for (int column = 0; column < source[row].length; column++) {
                if (random.nextDouble() < p) received[row][column] ^= 1;
            }
        }
        return received;
    }

    private static int[][] decodeProduct(int[][] received, int maxIterations) {
        int[][] current = copy(received);
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            int[][] before = copy(current);
            // Кожний рядок має довжину 22 і належить горизонтальному коду (22,17).
            for (int row = 0; row < VERTICAL_N; row++) {
                current[row] = correctHamming(current[row], HORIZONTAL_N);
            }
            // Кожний стовпець має довжину 10 і належить вертикальному коду (10,6).
            for (int column = 0; column < HORIZONTAL_N; column++) {
                int[] word = new int[VERTICAL_N];
                for (int row = 0; row < VERTICAL_N; row++) word[row] = current[row][column];
                int[] corrected = correctHamming(word, VERTICAL_N);
                for (int row = 0; row < VERTICAL_N; row++) current[row][column] = corrected[row];
            }
            if (Arrays.deepEquals(before, current)) break;
        }
        return current;
    }

    private static int[] correctHamming(int[] received, int n) {
        int syndrome = 0;
        for (int parityPosition = 1; parityPosition <= n; parityPosition <<= 1) {
            int parity = 0;
            for (int position = 1; position <= n; position++) {
                if ((position & parityPosition) != 0) parity ^= received[position - 1];
            }
            if (parity != 0) syndrome |= parityPosition;
        }
        int[] corrected = received.clone();
        if (syndrome >= 1 && syndrome <= n) corrected[syndrome - 1] ^= 1;
        return corrected;
    }

    private static int[][] extractInformation(int[][] code) {
        int[][] verticalCode = new int[VERTICAL_N][HORIZONTAL_K];
        for (int row = 0; row < VERTICAL_N; row++) {
            verticalCode[row] = extractData(code[row], HORIZONTAL_K);
        }
        int[][] information = new int[VERTICAL_K][HORIZONTAL_K];
        for (int column = 0; column < HORIZONTAL_K; column++) {
            int[] word = new int[VERTICAL_N];
            for (int row = 0; row < VERTICAL_N; row++) word[row] = verticalCode[row][column];
            int[] data = extractData(word, VERTICAL_K);
            for (int row = 0; row < VERTICAL_K; row++) information[row][column] = data[row];
        }
        return information;
    }

    private static int[] extractData(int[] codeword, int k) {
        int[] data = new int[k];
        int index = 0;
        for (int position = 1; position <= codeword.length && index < k; position++) {
            if (!isParityPosition(position)) data[index++] = codeword[position - 1];
        }
        return data;
    }

    private static boolean isParityPosition(int position) {
        return (position & (position - 1)) == 0;
    }

    private static int countDifferences(int[][] a, int[][] b) {
        int count = 0;
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[i].length; j++) if (a[i][j] != b[i][j]) count++;
        }
        return count;
    }

    private static int[][] copy(int[][] matrix) {
        int[][] result = new int[matrix.length][];
        for (int i = 0; i < matrix.length; i++) result[i] = matrix[i].clone();
        return result;
    }

    private static void printMatrix(int[][] matrix) {
        for (int[] row : matrix) {
            StringBuilder line = new StringBuilder();
            for (int bit : row) line.append(bit).append(' ');
            System.out.println(line.toString().trim());
        }
    }
}
