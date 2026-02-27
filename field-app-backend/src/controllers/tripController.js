const Trip = require('../models/Trip');
const { syncToSecondary } = require('../config/dbSync');

// @desc    Submit a trip expense
// @route   POST /api/trips/submit
// @access  Private
exports.submitTrip = async (req, res) => {
  try {
    const {
      stationVisited,
      periodFrom,
      periodTo,
      advanceAmount,
      expenses // JSON string in multipart, parsed object in application/json
    } = req.body;

    if (!stationVisited || !periodFrom || !periodTo) {
      return res.status(400).json({
        success: false,
        message: 'Station visited, period from and period to are required'
      });
    }

    // expenses arrives as a JSON string when sent via multipart/form-data
    let parsedExpenses;
    try {
      parsedExpenses = typeof expenses === 'string' ? JSON.parse(expenses) : expenses;
    } catch (e) {
      return res.status(400).json({ success: false, message: 'Invalid expenses JSON' });
    }

    if (!parsedExpenses || !Array.isArray(parsedExpenses) || parsedExpenses.length === 0) {
      return res.status(400).json({
        success: false,
        message: 'At least one expense item is required'
      });
    }

    // ── Receipt → Expense mapping ─────────────────────────────────────────────
    //
    // HOW IT WORKS (safe for multi-user concurrent requests):
    //
    // Each request is fully isolated by Express/Multer — req.files only contains
    // the files from THIS request, never mixed with another user's upload.
    //
    // Within a single request we still need to know WHICH expense each receipt
    // belongs to, because not every expense has a receipt (sparse case).
    //
    // The Android app encodes the expense array index into the filename:
    //   "expIdx_{expenseIndex}_{timestamp}.jpg"
    //   e.g. "expIdx_2_1709040123456.jpg"  → belongs to parsedExpenses[2]
    //
    // We parse that prefix here to build a Map<expenseIndex, cloudinaryUrl>
    // and then assign receipts to the correct expense slot.
    // If the filename doesn't have the prefix (e.g. retried upload from an
    // older app version) we fall back to sequential assignment so old data
    // still works.
    //
    const receiptMap = {}; // { expenseIndex: cloudinaryUrl }

    if (req.files && req.files.length > 0) {
      let fallbackIndex = 0;

      req.files.forEach(file => {
        const url = file.path; // Cloudinary secure URL set by multer-storage-cloudinary

        // Try to parse "expIdx_{n}_{timestamp}.jpg" from the original filename
        const match = file.originalname.match(/^expIdx_(\d+)_/);
        if (match) {
          const expenseIndex = parseInt(match[1], 10);
          receiptMap[expenseIndex] = url;
        } else {
          // Fallback: assign sequentially (handles retries from older app builds)
          receiptMap[fallbackIndex] = url;
          fallbackIndex++;
        }
      });
    }

    // Build expense items with correctly mapped receipt URLs
    const expenseItems = parsedExpenses.map((item, index) => ({
      expenseType: item.expenseType,
      details:     item.details     || '',
      travelFrom:  item.travelFrom  || '',
      travelTo:    item.travelTo    || '',
      travelMode:  item.travelMode  || '',
      daysCount:   parseInt(item.daysCount)    || 0,
      ratePerDay:  parseFloat(item.ratePerDay) || 0,
      amount:      parseFloat(item.amount)     || 0,
      receiptUrl:  receiptMap[index] || null   // null if this expense has no receipt
    }));

    const totalAmount   = expenseItems.reduce((sum, e) => sum + e.amount, 0);
    const advance       = parseFloat(advanceAmount) || 0;
    const payableAmount = totalAmount - advance;

    const trip = await Trip.create({
      userId:        req.user._id,
      employeeId:    req.user.employeeId,
      employeeName:  req.user.name,
      designation:   req.user.designation || '',
      stationVisited,
      periodFrom,
      periodTo,
      advanceAmount: advance,
      expenses:      expenseItems,
      totalAmount,
      payableAmount
    });

    console.log(`✅ Trip submitted: ${trip._id} by ${req.user.employeeId} (${req.user.name}) → ${stationVisited} | items: ${expenseItems.length} | receipts: ${Object.keys(receiptMap).length}`);

    // Non-blocking sync to secondary DB
    syncToSecondary('Trip', Trip.schema, trip.toObject())
      .catch(err => console.error('⚠️ Secondary sync failed:', err?.message));

    res.status(201).json({
      success: true,
      message: 'Trip expenses submitted successfully',
      data: trip
    });

  } catch (error) {
    console.error('❌ Trip submission error:', error?.message || error);
    res.status(500).json({
      success: false,
      message: error?.message || 'Server error'
    });
  }
};

// @desc    Get all trips for logged in user
// @route   GET /api/trips
// @access  Private
exports.getMyTrips = async (req, res) => {
  try {
    const trips = await Trip.find({ userId: req.user._id })
      .sort({ createdAt: -1 });

    res.status(200).json({
      success: true,
      count: trips.length,
      data: trips
    });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
};

// @desc    Get single trip
// @route   GET /api/trips/:id
// @access  Private
exports.getTrip = async (req, res) => {
  try {
    const trip = await Trip.findOne({
      _id: req.params.id,
      userId: req.user._id  // scoped to the requesting user — no cross-user access
    });

    if (!trip) {
      return res.status(404).json({ success: false, message: 'Trip not found' });
    }

    res.status(200).json({ success: true, data: trip });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
};

// @desc    Get trip expense stats for logged in user
// @route   GET /api/trips/stats
// @access  Private
exports.getTripStats = async (req, res) => {
  try {
    const [pending, approved, total] = await Promise.all([
      Trip.aggregate([
        { $match: { userId: req.user._id, status: 'pending' } },
        { $group: { _id: null, total: { $sum: '$totalAmount' } } }
      ]),
      Trip.aggregate([
        { $match: { userId: req.user._id, status: 'approved' } },
        { $group: { _id: null, total: { $sum: '$totalAmount' } } }
      ]),
      Trip.countDocuments({ userId: req.user._id })
    ]);

    res.status(200).json({
      success: true,
      data: {
        totalPending:   pending[0]?.total  || 0,
        totalApproved:  approved[0]?.total || 0,
        tripCount:      total
      }
    });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
};

// ── Admin endpoints ───────────────────────────────────────────────

// @desc    Get all trips (admin)
// @route   GET /api/trips/admin/all
// @access  Admin
exports.getAllTrips = async (req, res) => {
  try {
    const { status, employeeId, page = 1, limit = 20 } = req.query;

    const query = {};
    if (status)     query.status     = status;
    if (employeeId) query.employeeId = employeeId;

    const skip = (parseInt(page) - 1) * parseInt(limit);

    const [trips, total] = await Promise.all([
      Trip.find(query)
        .sort({ createdAt: -1 })
        .skip(skip)
        .limit(parseInt(limit)),
      Trip.countDocuments(query)
    ]);

    res.status(200).json({
      success: true,
      count: trips.length,
      total,
      page:  parseInt(page),
      pages: Math.ceil(total / parseInt(limit)),
      data:  trips
    });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
};

// @desc    Approve or reject a trip (admin)
// @route   PUT /api/trips/admin/:id/status
// @access  Admin
exports.updateTripStatus = async (req, res) => {
  try {
    const { status, adminNote } = req.body;

    if (!['approved', 'rejected'].includes(status)) {
      return res.status(400).json({
        success: false,
        message: 'Status must be approved or rejected'
      });
    }

    const trip = await Trip.findByIdAndUpdate(
      req.params.id,
      {
        status,
        adminNote:   adminNote || '',
        reviewedBy:  req.user._id,
        reviewedAt:  new Date()
      },
      { new: true }
    );

    if (!trip) {
      return res.status(404).json({ success: false, message: 'Trip not found' });
    }

    console.log(`✅ Trip ${trip._id} ${status} by admin ${req.user.employeeId}`);

    syncToSecondary('Trip', Trip.schema, trip.toObject())
      .catch(err => console.error('⚠️ Secondary sync failed:', err?.message));

    res.status(200).json({
      success: true,
      message: `Trip ${status} successfully`,
      data: trip
    });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
};

// @desc    Admin stats overview
// @route   GET /api/trips/admin/stats
// @access  Admin
exports.getAdminTripStats = async (req, res) => {
  try {
    const [pendingCount, approvedCount, rejectedCount, totalAmount] = await Promise.all([
      Trip.countDocuments({ status: 'pending' }),
      Trip.countDocuments({ status: 'approved' }),
      Trip.countDocuments({ status: 'rejected' }),
      Trip.aggregate([
        { $match: { status: 'approved' } },
        { $group: { _id: null, total: { $sum: '$totalAmount' } } }
      ])
    ]);

    res.status(200).json({
      success: true,
      data: {
        pending:             pendingCount,
        approved:            approvedCount,
        rejected:            rejectedCount,
        totalApprovedAmount: totalAmount[0]?.total || 0
      }
    });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
};