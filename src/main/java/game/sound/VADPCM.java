package game.sound;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import util.DynamicByteBuffer;
import util.Logger;

public class VADPCM
{
	private static final int FRAME_LENGTH = 9;
	public static final int ORDER = 2;

	public static class CodeBook
	{
		private final int numPred;
		private final int[][][] predictors;

		public CodeBook(ByteBuffer bb, int offset, int numPred)
		{
			this.numPred = numPred;
			this.predictors = new int[numPred][8][ORDER + 8];

			bb.position(offset);

			for (int i = 0; i < numPred; i++) {
				for (int j = 0; j < ORDER; j++) {
					for (int k = 0; k < 8; k++) {
						predictors[i][k][j] = bb.getShort();
					}
				}

				for (int k = 1; k < 8; k++) {
					predictors[i][k][ORDER] = predictors[i][k - 1][ORDER - 1];
				}

				predictors[i][0][ORDER] = 1 << 11; // 0x800

				for (int k = 1; k < 8; k++) {
					for (int j = 0; j < k; j++) {
						predictors[i][j][k + ORDER] = 0;
					}

					for (int j = k; j < 8; j++) {
						predictors[i][j][k + ORDER] = predictors[i][j - k][ORDER];
					}
				}
			}
		}

		public void write(DynamicByteBuffer dbb)
		{
			for (int i = 0; i < numPred; i++) {
				for (int j = 0; j < ORDER; j++) {
					for (int k = 0; k < 8; k++) {
						dbb.putShort(predictors[i][k][j]);
					}
				}
			}
		}
	}

	public static ArrayList<Short> decode(ByteBuffer bb, CodeBook book, int pos, int length)
	{
		ArrayList<Short> samples = new ArrayList<>();

		int numFrames = length / FRAME_LENGTH;
		int[] state = new int[16];

		bb.position(pos);
		for (int frame = 0; frame < numFrames; frame++) {
			// read frame header byte
			int header = bb.get() & 0xFF;

			// extract header byte fields
			int scale = 1 << (header >> 4);
			int bestPred = header & 0xF;

			if (bestPred >= book.numPred)
				bestPred = Math.min(bestPred, book.numPred - 1);

			int[] rawSamples = new int[16];

			// read frame sample bytes
			for (int i = 0; i < 8; i++) {
				int v = bb.get() & 0xFF;

				// extract 4-bit sample pair
				int s1 = v >> 4;
				int s2 = v & 0xF;

				// 4-bit sign extension
				s1 = (s1 << 28) >> 28;
				s2 = (s2 << 28) >> 28;

				// apply scale factor
				s1 *= scale;
				s2 *= scale;

				rawSamples[2 * i] = s1;
				rawSamples[2 * i + 1] = s2;
			}

			for (int j = 0; j < 2; j++) {
				int[] inVec = new int[16];

				if (j == 0)
					System.arraycopy(state, 16 - ORDER, inVec, 0, ORDER);
				else
					System.arraycopy(state, 8 - ORDER, inVec, 0, ORDER);

				for (int i = 0; i < 8; i++) {
					int idx = j * 8 + i;
					inVec[ORDER + i] = rawSamples[idx];
					state[idx] = rawSamples[idx] + innerProduct(ORDER + i, book.predictors[bestPred][i], inVec);
				}
			}

			for (int i : state) {
				samples.add((short) i);
			}
		}

		return samples;
	}

	public static class EncodeData
	{
		public final ByteBuffer buffer;
		public final short[] loopState;

		public EncodeData(ByteBuffer buffer, short[] loopState)
		{
			this.buffer = buffer;
			this.loopState = loopState;
		}
	}

	/**
	 * Encodes raw audio samples using VADPCM.
	 *
	 * @param samples Raw audio samples.
	 * @param book    Precomputed predictor codebook.
	 * @return ByteBuffer containing encoded VADPCM audio.
	 */
	public static EncodeData encode(List<Short> samples, CodeBook book, int loopStart)
	{
		int numSamples = samples.size();
		int encodedSize = 9 * (1 + (numSamples / 16)); // 16 samples -> 9

		// output
		ByteBuffer encoded = ByteBuffer.allocateDirect(encodedSize);
		short[] loopState = new short[16];

		int[] predCount = new int[book.numPred];

		// state
		short[] buffer = new short[16];
		int[] state = new int[16];
		int pos = 0;

		while (pos < numSamples) {
			int remaining = Math.min(16, numSamples - pos);

			// load frame samples into buffer; pad with zeros if necessary
			if (numSamples - pos >= remaining) {
				for (int i = 0; i < remaining; i++) {
					// check loop start condition for each sample
					if (loopStart > 0 && pos == loopStart) {
						for (int j = 0; j < 16; j++) {
							if (state[j] > Short.MAX_VALUE)
								loopState[j] = Short.MAX_VALUE;
							else if (state[j] < Short.MIN_VALUE)
								loopState[j] = Short.MIN_VALUE;
							else
								loopState[j] = (short) state[j];
						}
					}

					buffer[i] = samples.get(pos);
					pos++;
				}
				for (int i = remaining; i < 16; i++) {
					buffer[i] = 0;
				}

				encodeFrame(encoded, book, buffer, state, predCount);
			}
			else {
				Logger.logError("Missed a frame!");
			}

		}

		encoded.flip();
		return new EncodeData(encoded, loopState);
	}

	/**
	 * Encodes a single 16-sample audio frame into a compact representation using VADPCM.
	 *
	 * <p>This method selects the best LPC predictor from the provided codebook by minimizing the prediction error,
	 * quantizes the residual error, applies scaling, and writes the encoded data to the output buffer.
	 *
	 * @param out        Buffer to store the encoded frame data.
	 * @param book       Predictor codebook.
	 * @param buffer     Input audio samples for the current frame.
	 * @param state      Current encoder state.
	 * @param predCount  Not required by the algorithm, just available for accounting.
	 */
	private static void encodeFrame(ByteBuffer out, CodeBook book, short[] buffer, int[] state, int[] predCount)
	{
		short[] ix = new short[16];
		int[] prediction = new int[16];
		int[] inVec = new int[16];
		int[] saveState = new int[16];
		float[] error = new float[16];
		int[] ie = new int[16];

		int encBits = 4;
		int llevel = -(1 << (encBits - 1));
		int ulevel = -llevel - 1;

		int scaleFactor = 16 - encBits;

		// determine best-fitting predictor
		float sumErrSq;
		float minErrSqr = Float.MAX_VALUE;
		int bestPred = 0;

		for (int k = 0; k < book.numPred; k++) {
			do_prediction(state, buffer, inVec, error, prediction, book.predictors, k);

			sumErrSq = 0.0f;
			for (float v : error) {
				sumErrSq += v * v;
			}

			if (sumErrSq < minErrSqr) {
				minErrSqr = sumErrSq;
				bestPred = k;
			}
		}

		predCount[bestPred]++;

		// run prediction with the best predictor
		do_prediction(state, buffer, inVec, error, prediction, book.predictors, bestPred);

		// clamp errors to 16-bit range
		clamp_wow(16, error, ie, 16);

		// scale down to 4-bit signed integer range

		// find the largest absolute  value
		int max = 0;
		for (int i = 0; i < 16; i++) {
			if (Math.abs(ie[i]) > Math.abs(max)) {
				max = ie[i];
			}
		}

		// choose a scale that works for all
		int scale;
		for (scale = 0; scale <= scaleFactor; scale++) {
			if (max <= ulevel && max >= llevel)
				break;
			max /= 2;
		}

		System.arraycopy(state, 0, saveState, 0, 16);

		// attempt to encode
		scale--;
		int maxClip;
		int nIter = 0;
		float err;

		do {
			nIter++;
			scale++;
			maxClip = 0;
			scale = Math.min(scale, 12);

			System.arraycopy(saveState, 16 - ORDER, inVec, 0, ORDER);

			for (int i = 0; i < 8; i++) {
				prediction[i] = innerProduct(ORDER + i, book.predictors[bestPred][i], inVec);
				err = buffer[i] - prediction[i];
				ix[i] = qSample(err, 1 << scale);
				int cV = clip(ix[i], llevel, ulevel) - ix[i];
				maxClip = Math.max(maxClip, Math.abs(cV));
				ix[i] += cV;
				inVec[i + ORDER] = ix[i] * (1 << scale);
				state[i] = prediction[i] + inVec[i + ORDER];
			}

			for (int i = 0; i < ORDER; i++) {
				inVec[i] = state[8 - ORDER + i];
			}

			for (int i = 0; i < 8; i++) {
				prediction[8 + i] = innerProduct(ORDER + i, book.predictors[bestPred][i], inVec);
				err = buffer[8 + i] - prediction[8 + i];
				ix[8 + i] = qSample(err, 1 << scale);
				int cV = clip(ix[8 + i], llevel, ulevel) - ix[8 + i];
				maxClip = Math.max(maxClip, Math.abs(cV));
				ix[8 + i] += cV;
				inVec[i + ORDER] = ix[8 + i] * (1 << scale);
				state[8 + i] = prediction[8 + i] + inVec[i + ORDER];
			}

		}
		while (maxClip >= 2 && nIter < 2);

		// write header
		out.put((byte) ((scale << 4) | (bestPred & 0xF)));

		// write encoded samples
		for (int i = 0; i < 16; i += 2) {
			out.put((byte) ((ix[i] << 4) | (ix[i + 1] & 0xF)));
		}
	}

	private static void do_prediction(int[] state, short[] buffer, int[] inVec, float[] error, int[] prediction,
		int[][][] coefTable, int k)
	{
		System.arraycopy(state, 16 - ORDER, inVec, 0, ORDER);

		for (int i = 0; i < 8; i++) {
			prediction[i] = innerProduct(i + ORDER, coefTable[k][i], inVec);
			inVec[i + ORDER] = buffer[i] - prediction[i];
			error[i] = inVec[i + ORDER];
		}

		for (int i = 0; i < ORDER; i++) {
			inVec[i] = prediction[8 - ORDER + i] + inVec[i + 8];
		}

		for (int i = 0; i < 8; i++) {
			prediction[8 + i] = innerProduct(ORDER + i, coefTable[k][i], inVec);
			inVec[i + ORDER] = buffer[i + 8] - prediction[i + 8];
			error[i + 8] = inVec[i + ORDER];
		}
	}

	private static int innerProduct(int len, int[] a, int[] b)
	{
		int out = 0;

		for (int k = 0; k < len; k++) {
			out += a[k] * b[k];
		}

		int dout = out / (1 << 11);
		int fiout = dout * (1 << 11);

		if ((out - fiout) < 0)
			return dout - 1;
		else
			return dout;
	}

	private static short qSample(float x, int scale)
	{
		if (x > 0.0f) {
			return (short) ((x / scale) + 0.4999999f);
		}
		else {
			return (short) ((x / scale) - 0.4999999f);
		}
	}

	private static void clamp_wow(int fs, float[] e, int[] ie, int bits)
	{
		float ulevel = (1 << (bits - 1)) - 1;
		float llevel = -ulevel - 1;

		for (int i = 0; i < fs; i++) {
			// clamp to level range
			if (e[i] > ulevel) {
				e[i] = ulevel;
			}
			if (e[i] < llevel) {
				e[i] = llevel;
			}

			// apply rounding
			if (e[i] > 0.0f) {
				ie[i] = (int) (e[i] + 0.5f);
			}
			else {
				ie[i] = (int) (e[i] - 0.5f);
			}
		}
	}

	private static int clip(int ix, int llevel, int ulevel)
	{
		if (ix < llevel) {
			return llevel;
		}
		if (ix > ulevel) {
			return ulevel;
		}
		return ix;
	}
}
