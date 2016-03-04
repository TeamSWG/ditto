package resources.network;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

import resources.collections.SWGList;
import resources.collections.SWGMap;
import resources.collections.SWGSet;
import resources.encodables.Encodable;
import utilities.Encoder.StringType;


public class NetBuffer {
	
	public static final Charset ASCII   = Charset.forName("UTF-8");
	public static final Charset UNICODE = Charset.forName("UTF-16LE");
	
	private final ByteBuffer data;
	private final int size;
	
	private NetBuffer(ByteBuffer data) {
		this.data = data;
		this.size = data.array().length;
	}
	
	public static final NetBuffer allocate(int size) {
		return new NetBuffer(ByteBuffer.allocate(size));
	}
	
	public static final NetBuffer wrap(byte [] data) {
		return new NetBuffer(ByteBuffer.wrap(data));
	}
	
	public static final NetBuffer wrap(ByteBuffer data) {
		return new NetBuffer(data);
	}
	
	public int remaining() {
		return data.remaining();
	}
	
	public int position() {
		return data.position();
	}
	
	public void position(int position) {
		data.position(position);
	}
	
	public void addBoolean(boolean b) {
		data.put(b ? (byte)1 : (byte)0);
	}
	
	public void addAscii(String s) {
		data.order(ByteOrder.LITTLE_ENDIAN);
		data.putShort((short)s.length());
		data.put(s.getBytes(ASCII));
	}
	
	public void addAscii(char [] s) {
		data.order(ByteOrder.LITTLE_ENDIAN);
		data.putShort((short)s.length);
		ByteBuffer bb = ASCII.encode(CharBuffer.wrap(s));
		byte [] bData = new byte[bb.limit()];
		bb.get(bData);
		data.put(bData);
	}
	
	public void addUnicode(String s) {
		data.order(ByteOrder.LITTLE_ENDIAN);
		data.putInt(s.length());
		data.put(s.getBytes(UNICODE));
	}
	
	public void addLong(long l) {
		data.order(ByteOrder.LITTLE_ENDIAN).putLong(l);
	}
	
	public void addInt(int i) {
		data.order(ByteOrder.LITTLE_ENDIAN).putInt(i);
	}
	
	public void addFloat(float f) {
		data.putFloat(f);
	}
	
	public void addShort(int i) {
		data.order(ByteOrder.LITTLE_ENDIAN).putShort((short)i);
	}
	
	public void addNetLong(long l) {
		data.order(ByteOrder.BIG_ENDIAN).putLong(l);
	}
	
	public void addNetInt(int i) {
		data.order(ByteOrder.BIG_ENDIAN).putInt(i);
	}
	
	public void addNetShort(int i) {
		data.order(ByteOrder.BIG_ENDIAN).putShort((short)i);
	}
	
	public void addByte(int b) {
		data.put((byte)b);
	}
	
	public void addArray(byte [] b) {
		addShort(b.length);
		data.put(b);
	}
	
	public boolean getBoolean() {
		return getByte() == 1 ? true : false;
	}
	
	public String getAscii() {
		data.order(ByteOrder.LITTLE_ENDIAN);
		short length = data.getShort();
		if (length > data.remaining())
			return "";
		byte [] str = new byte[length];
		data.get(str);
		return new String(str, ASCII);
	}
	
	public String getUnicode() {
		data.order(ByteOrder.LITTLE_ENDIAN);
		int length = data.getInt() * 2;
		if (length > data.remaining())
			return "";
		byte [] str = new byte[length];
		data.get(str);
		return new String(str, UNICODE);
	}
	
	public String getString(StringType type) {
		switch (type) {
			case ASCII:
				return getAscii();
			case UNICODE:
				return getUnicode();
			default:
				return null;
		}
	}
	
	public byte getByte() {
		return data.get();
	}
	
	public short getShort() {
		return data.order(ByteOrder.LITTLE_ENDIAN).getShort();
	}
	
	public int getInt() {
		return data.order(ByteOrder.LITTLE_ENDIAN).getInt();
	}
	
	public float getFloat() {
		return data.getFloat();
	}
	
	public long getLong() {
		return data.order(ByteOrder.LITTLE_ENDIAN).getLong();
	}
	
	public short getNetShort() {
		return data.order(ByteOrder.BIG_ENDIAN).getShort();
	}
	
	public int getNetInt() {
		return data.order(ByteOrder.BIG_ENDIAN).getInt();
	}
	
	public long getNetLong() {
		return data.order(ByteOrder.BIG_ENDIAN).getLong();
	}
	
	public byte [] getArray() {
		byte [] bData = new byte[getShort()];
		data.get(bData);
		return bData;
	}
	
	public <T> Object getGeneric(Class<T> type) {
		if (Encodable.class.isAssignableFrom(type)) {
			T instance = null;
			try {
				instance = type.newInstance();
				((Encodable) instance).decode(data);
			} catch (InstantiationException | IllegalAccessException e) {
				e.printStackTrace();
			}

			return instance;
		} else if (Integer.class.isAssignableFrom(type) || Integer.TYPE.isAssignableFrom(type))
			return getInt();
		else if (Long.class.isAssignableFrom(type) || Long.TYPE.isAssignableFrom(type))
			return getLong();
		else if (Float.class.isAssignableFrom(type) || Float.TYPE.isAssignableFrom(type))
			return getFloat();
		else if (StringType.ASCII.getClass().isAssignableFrom(type))
			return getAscii();
		else if (StringType.UNICODE.getClass().isAssignableFrom(type))
			return getAscii();
		return null;
	}
	
	public <T extends Encodable> T getEncodable(Class<T> type) {
		T instance = null;
		try {
			instance = type.newInstance();
			instance.decode(data);
		} catch (InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
		}

		return instance;
	}
	
	public SWGSet<String> getSwgSet(int num, int var, StringType type) {
		SWGSet<String> set = new SWGSet<>(num, var);
		set.decode(data, type);
		return set;
	}
	
	public <T> SWGSet<T> getSwgSet(int num, int var, Class<T> type) {
		SWGSet<T> set = new SWGSet<>(num, var);
		set.decode(data, type);
		return set;
	}
	
	public SWGList<String> getSwgList(int num, int var, StringType type) {
		SWGList<String> set = new SWGList<>(num, var);
		set.decode(data, type);
		return set;
	}
	
	
	public <T> SWGList<T> getSwgList(int num, int var, Class<T> type) {
		SWGList<T> set = new SWGList<>(num, var);
		set.decode(data, type);
		return set;
	}
	
	public SWGMap<String, String> getSwgMap(int num, int var, StringType key, StringType val) {
		return getSwgMap(num, var, key, val, false);
	}
	
	public SWGMap<String, String> getSwgMap(int num, int var, StringType key, StringType val, boolean addByte) {
		SWGMap<String, String> map = new SWGMap<>(num, var);
		map.decode(data, key, val, addByte);
		return map;
	}
	
	public <V> SWGMap<String, V> getSwgMap(int num, int var, StringType key, Class<V> val) {
		return getSwgMap(num, var, key, val, false);
	}
	
	public <V> SWGMap<String, V> getSwgMap(int num, int var, StringType key, Class<V> val, boolean addByte) {
		SWGMap<String, V> map = new SWGMap<>(num, var);
		map.decode(data, key, val, addByte);
		return map;
	}
	
	public <K, V> SWGMap<K, V> getSwgMap(int num, int var, Class<K> key, Class<V> val) {
		return getSwgMap(num, var, key, val, false);
	}
	
	public <K, V> SWGMap<K, V> getSwgMap(int num, int var, Class<K> key, Class<V> val, boolean addByte) {
		SWGMap<K, V> map = new SWGMap<>(num, var);
		map.decode(data, key, val, addByte);
		return map;
	}
	
	public byte [] array() {
		return data.array();
	}
	
	public int size() {
		return size;
	}
	
	public byte [] copyArray() {
		return copyArray(0, size);
	}
	
	public byte [] copyArray(int offset, int length) {
		if (length < 0)
			throw new IllegalArgumentException("Length cannot be less than 0!");
		if (offset+length > size)
			throw new IllegalArgumentException("Length extends past the end of the array!");
		byte [] ret = new byte[length];
		System.arraycopy(array(), 0, ret, offset, length);
		return ret;
	}
	
}