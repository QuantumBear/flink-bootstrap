package com.mvad.flink.demo.streaming.io.elephantbird;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A class to write blocks of serialized objects.
 */
public class BinaryBlockWriter<M> {
  protected static final int DEFAULT_NUM_RECORDS_PER_BLOCK = 100;

  public static final byte[] KNOWN_GOOD_POSITION_MARKER = new byte[] { 0x29, (byte)0xd8, (byte)0xd5, 0x06, 0x58,
      (byte)0xcd, 0x4c, 0x29, (byte)0xb2,
      (byte)0xbc, 0x57, (byte)0x99, 0x21, 0x71,
      (byte)0xbd, (byte)0xff };

  private final OutputStream out_;
  private final int numRecordsPerBlock_;
  protected final Class<M> innerClass_;
  private final BinaryConverter<M> binaryConverter_;
  private int numRecordsWritten_ = 0;
  private List<ByteString> protoBlobs_;

  protected BinaryBlockWriter(OutputStream out, Class<M> protoClass, BinaryConverter<M> binaryConverter, int numRecordsPerBlock) {
    out_ = out;
    numRecordsPerBlock_ = numRecordsPerBlock;
    innerClass_ = protoClass;
    binaryConverter_ = binaryConverter;
    protoBlobs_ = new ArrayList<ByteString>(numRecordsPerBlock_);
  }

  public BinaryBlockWriter(OutputStream out, Class<M> protoClass, BinaryConverter<M> binaryConverter) {
    this(out, protoClass, binaryConverter, DEFAULT_NUM_RECORDS_PER_BLOCK);
  }

  public void write(M message) throws IOException {
    if (message instanceof Message) {
      //a small hack to avoid extra copy, since we need a ByteString anyway.
      protoBlobs_.add(((Message) message).toByteString());
    } else {
      protoBlobs_.add(ByteString.copyFrom(binaryConverter_.toBytes(message)));
    }

    numRecordsWritten_++;

    if (protoBlobs_.size() == numRecordsPerBlock_) {
      serialize();
    }
  }

  public void finish() throws IOException {
    if (protoBlobs_.size() > 0) {
      serialize();
    }
  }

  public void close() throws IOException {
    finish();
    out_.close();
  }

  protected void serialize() throws IOException {
    out_.write(KNOWN_GOOD_POSITION_MARKER);
    Message block = SerializedBlock
        .newInstance(innerClass_.getCanonicalName(),protoBlobs_)
        .getMessage();
    protoBlobs_ = new ArrayList<ByteString>(numRecordsPerBlock_);
    writeRawLittleEndian32(block.getSerializedSize());
    block.writeTo(out_);
  }

  private void writeRawLittleEndian32(int size) throws IOException {
    out_.write((size) & 0xFF);
    out_.write((size >> 8) & 0xFF);
    out_.write((size >> 16) & 0xFF);
    out_.write((size >> 24) & 0xFF);
  }
}
