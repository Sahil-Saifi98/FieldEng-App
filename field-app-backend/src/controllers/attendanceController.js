const Attendance = require('../models/Attendance');
const moment = require('moment');
const { syncToSecondary } = require('../config/dbSync');
const { getAddressFromCoordinates } = require('../utils/geocoder');
const { cloudinary } = require('../config/cloudinary');

// Helper: upload buffer to Cloudinary with retry
const uploadToCloudinaryWithRetry = async (file, retries = 3) => {
  for (let attempt = 1; attempt <= retries; attempt++) {
    try {
      console.log(`☁️ Cloudinary upload attempt ${attempt}/${retries}`);

      // If file already has a Cloudinary path (multer-storage-cloudinary succeeded)
      if (file.path && file.path.startsWith('http')) {
        console.log('✅ File already uploaded by multer-storage-cloudinary');
        return file.path;
      }

      // Manual fallback upload using buffer
      const result = await new Promise((resolve, reject) => {
        const uploadStream = cloudinary.uploader.upload_stream(
          {
            folder: 'fieldapp/selfies',
            transformation: [
              { width: 600, height: 600, crop: 'limit' },
              { quality: 'auto:low' }
            ],
            timeout: 120000
          },
          (error, result) => {
            if (error) reject(error);
            else resolve(result);
          }
        );
        uploadStream.end(file.buffer);
      });

      console.log(`✅ Cloudinary upload succeeded on attempt ${attempt}`);
      return result.secure_url;

    } catch (err) {
      console.error(`❌ Cloudinary attempt ${attempt} failed:`, err?.message || err);
      if (attempt === retries) throw err;
      // Wait before retry (1s, 2s, 3s)
      await new Promise(r => setTimeout(r, attempt * 1000));
    }
  }
};

// @desc    Submit attendance
// @route   POST /api/attendance/submit
// @access  Private
exports.submitAttendance = async (req, res) => {
  try {
    const { latitude, longitude, timestamp } = req.body;

    console.log('📥 Received attendance submission:', {
      userId: req.user._id,
      employeeId: req.user.employeeId,
      latitude,
      longitude,
      timestamp,
      hasFile: !!req.file
    });

    if (!req.file) {
      return res.status(400).json({
        success: false,
        message: 'Selfie image is required'
      });
    }

    const lat = parseFloat(latitude);
    const lng = parseFloat(longitude);

    if (isNaN(lat) || isNaN(lng)) {
      return res.status(400).json({
        success: false,
        message: 'Invalid coordinates provided'
      });
    }

    // Upload to Cloudinary with retry
    let selfieUrl;
    try {
      selfieUrl = await uploadToCloudinaryWithRetry(req.file);
    } catch (uploadError) {
      console.error('❌ All Cloudinary upload attempts failed:', uploadError?.message || uploadError);
      return res.status(500).json({
        success: false,
        message: 'Failed to upload selfie. Please check your internet connection and try again.'
      });
    }

    // Get address with fallback
    let address = `${lat}, ${lng}`;
    try {
      address = await getAddressFromCoordinates(lat, lng);
      console.log('📍 Address:', address);
    } catch (geoError) {
      console.error('⚠️ Geocoding failed, using coordinates:', geoError.message);
    }

    const timestampDate = new Date(parseInt(timestamp));
    const date = moment(timestampDate).format('YYYY-MM-DD');
    const checkInTime = moment(timestampDate).format('HH:mm:ss');

    const attendance = await Attendance.create({
      userId: req.user._id,
      employeeId: req.user.employeeId,
      selfiePath: selfieUrl,
      latitude: lat,
      longitude: lng,
      address: address,
      timestamp: timestampDate,
      date: date,
      checkInTime: checkInTime
    });

    console.log('✅ Attendance created:', {
      id: attendance._id,
      employeeId: attendance.employeeId,
      address: attendance.address
    });

    // Sync to secondary (non-blocking — don't fail if this fails)
    syncToSecondary('Attendance', Attendance.schema, attendance.toObject())
      .catch(err => console.error('⚠️ Secondary sync failed (non-critical):', err.message));

    res.status(201).json({
      success: true,
      message: 'Attendance submitted successfully',
      data: attendance
    });

  } catch (error) {
    console.error('❌ Attendance submission error:', error?.message || error);
    res.status(500).json({
      success: false,
      message: error?.message || 'Server error. Please try again.'
    });
  }
};

// @desc    Get today's attendance
// @route   GET /api/attendance/today
// @access  Private
exports.getTodayAttendance = async (req, res) => {
  try {
    const today = moment().format('YYYY-MM-DD');
    const attendance = await Attendance.find({
      userId: req.user._id,
      date: today
    }).sort({ timestamp: -1 });

    res.status(200).json({
      success: true,
      count: attendance.length,
      data: attendance
    });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
};

// @desc    Get attendance by date range
// @route   GET /api/attendance
// @access  Private
exports.getAttendance = async (req, res) => {
  try {
    const { startDate, endDate } = req.query;
    let query = { userId: req.user._id };

    if (startDate && endDate) {
      query.date = { $gte: startDate, $lte: endDate };
    }

    const attendance = await Attendance.find(query).sort({ timestamp: -1 });
    res.status(200).json({ success: true, count: attendance.length, data: attendance });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
};

// @desc    Get attendance statistics
// @route   GET /api/attendance/stats
// @access  Private
exports.getAttendanceStats = async (req, res) => {
  try {
    const today = moment().format('YYYY-MM-DD');
    const thisMonth = moment().format('YYYY-MM');

    const [todayCount, monthCount, totalCount] = await Promise.all([
      Attendance.countDocuments({ userId: req.user._id, date: today }),
      Attendance.countDocuments({ userId: req.user._id, date: { $regex: `^${thisMonth}` } }),
      Attendance.countDocuments({ userId: req.user._id })
    ]);

    res.status(200).json({
      success: true,
      data: { today: todayCount, thisMonth: monthCount, total: totalCount }
    });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
};

// @desc    Delete attendance record
// @route   DELETE /api/attendance/:id
// @access  Private
exports.deleteAttendance = async (req, res) => {
  try {
    const attendance = await Attendance.findById(req.params.id);

    if (!attendance) {
      return res.status(404).json({ success: false, message: 'Attendance record not found' });
    }

    if (attendance.userId.toString() !== req.user._id.toString()) {
      return res.status(403).json({ success: false, message: 'Not authorized' });
    }

    await attendance.deleteOne();
    res.status(200).json({ success: true, message: 'Attendance record deleted successfully' });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
};