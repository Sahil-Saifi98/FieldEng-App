const Attendance = require('../models/Attendance');
const User = require('../models/User');
const moment = require('moment-timezone');
const IST = 'Asia/Kolkata';

// Convert any timestamp to IST — used for all exports so times are always IST
const toISTTime = (ts) => moment(ts).tz('Asia/Kolkata').format('HH:mm:ss');
const toISTDate = (ts) => moment(ts).tz('Asia/Kolkata').format('YYYY-MM-DD');
// Always recompute from raw timestamp so even old stored-UTC records export correctly
const getISTTime = (att) => att.timestamp ? toISTTime(att.timestamp) : (att.checkInTime || '');
const getISTDate = (att) => att.timestamp ? toISTDate(att.timestamp) : (att.date || '');
const archiver = require('archiver');
const PDFDocument = require('pdfkit');
const axios = require('axios');
const fs = require('fs');
const path = require('path');
const { promisify } = require('util');
const stream = require('stream');
const pipeline = promisify(stream.pipeline);
const { generateTripPdf, downloadReceiptToTemp } = require('./tripExportController');

// Helper function to download image from URL
async function downloadImage(url, filepath) {
  try {
    // Fix backslashes in URL and ensure proper URL format
    let cleanUrl = url.replace(/\\/g, '/');
    
    // If URL is a relative path (starts with uploads/), prepend the base URL
    if (cleanUrl.startsWith('uploads/')) {
      // Get the base URL from environment or use default
      const baseUrl = process.env.BASE_URL || 'https://asap-kc7n.onrender.com';
      cleanUrl = `${baseUrl}/${cleanUrl}`;
    }
    
    console.log(`Downloading: ${cleanUrl}`);
    
    const response = await axios({
      method: 'GET',
      url: cleanUrl,
      responseType: 'stream',
      timeout: 30000
    });
    
    const writer = fs.createWriteStream(filepath);
    await pipeline(response.data, writer);
    return true;
  } catch (error) {
    console.error(`Failed to download image from ${url}:`, error.message);
    return false;
  }
}

// @desc    Get all users (Admin only)
// @route   GET /api/admin/users
// @access  Private/Admin
exports.getAllUsers = async (req, res) => {
  try {
    const users = await User.find().select('-password');
    
    res.status(200).json({
      success: true,
      count: users.length,
      data: users
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};

// @desc    Get all attendance records (Admin only)
// @route   GET /api/admin/attendance
// @access  Private/Admin
// In your admin attendance query
exports.getAllAttendance = async (req, res) => {
  try {
    const { startDate, endDate, employeeId } = req.query;
    
    let query = {};
    
    if (startDate && endDate) {
      query.date = { $gte: startDate, $lte: endDate };
    }
    
    if (employeeId) {
      query.employeeId = employeeId;
    }

    const attendance = await Attendance.find(query)
      .populate('userId', 'name employeeId email department designation')
      .sort({ timestamp: -1 });

    // Address is already in the attendance records
    res.status(200).json({
      success: true,
      count: attendance.length,
      data: attendance // Address field included automatically
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};

// @desc    Get attendance by user ID (Admin only)
// @route   GET /api/admin/attendance/:userId
// @access  Private/Admin
exports.getUserAttendance = async (req, res) => {
  try {
    const attendance = await Attendance.find({ userId: req.params.userId })
      .populate('userId', 'name employeeId email')
      .sort({ timestamp: -1 });

    const attendanceWithUrls = attendance.map(att => {
      const attendanceObj = att.toObject();
      return {
        ...attendanceObj,
        selfieUrl: attendanceObj.selfiePath,
        userName: att.userId ? att.userId.name : 'Unknown User'
      };
    });

    res.status(200).json({
      success: true,
      count: attendanceWithUrls.length,
      data: attendanceWithUrls
    });
  } catch (error) {
    console.error('Get user attendance error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};

// @desc    Get attendance statistics for all users (Admin only)
// @route   GET /api/admin/stats
// @access  Private/Admin
exports.getAdminStats = async (req, res) => {
  try {
    const today = moment().format('YYYY-MM-DD');
    const thisMonth = moment().format('YYYY-MM');

    const totalUsers = await User.countDocuments();
    const activeUsers = await User.countDocuments({ isActive: true });
    
    const todayAttendance = await Attendance.countDocuments({ date: today });
    const monthAttendance = await Attendance.countDocuments({ 
      date: { $regex: `^${thisMonth}` } 
    });

    const topUsers = await Attendance.aggregate([
      { $match: { date: { $regex: `^${thisMonth}` } } },
      { $group: { _id: '$employeeId', count: { $sum: 1 } } },
      { $sort: { count: -1 } },
      { $limit: 5 }
    ]);

    res.status(200).json({
      success: true,
      data: {
        totalUsers,
        activeUsers,
        todayAttendance,
        monthAttendance,
        topUsers
      }
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};

// @desc    Delete user (Admin only)
// @route   DELETE /api/admin/users/:id
// @access  Private/Admin
exports.deleteUser = async (req, res) => {
  try {
    const user = await User.findById(req.params.id);

    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    await user.deleteOne();

    res.status(200).json({
      success: true,
      message: 'User deleted successfully'
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};

// @desc    Toggle user active status (Admin only)
// @route   PUT /api/admin/users/:id/toggle-active
// @access  Private/Admin
exports.toggleUserActive = async (req, res) => {
  try {
    const user = await User.findById(req.params.id);

    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    user.isActive = !user.isActive;
    await user.save();

    res.status(200).json({
      success: true,
      message: `User ${user.isActive ? 'activated' : 'deactivated'} successfully`,
      data: user
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};

// @desc    Export user data with attendance and selfies as ZIP
// @route   POST /api/admin/export/user/:userId
// @access  Private/Admin

// @desc    Export user data as ZIP  (attendance + selfies, expenses + receipts, or both)
// @route   POST /api/admin/export/user/:userId
// @access  Private/Admin
exports.exportUserData = async (req, res) => {
  const tempDir = path.join(__dirname, '../../temp');
  const userTempDir = path.join(tempDir, `user_${req.params.userId}_${Date.now()}`);

  try {
    const { startDate, endDate, exportType = 'all' } = req.body;
    // exportType: 'attendance' | 'expenses' | 'all'
    const userId = req.params.userId;

    console.log('Export request:', { userId, startDate, endDate, exportType });

    req.setTimeout(0);
    res.setTimeout(0);

    if (!fs.existsSync(tempDir)) fs.mkdirSync(tempDir, { recursive: true });
    fs.mkdirSync(userTempDir, { recursive: true });

    const user = await User.findById(userId).select('-password');
    if (!user) {
      return res.status(404).json({ success: false, message: 'User not found' });
    }

    // ── ATTENDANCE: CSV + selfie images ──────────────────────────────
    let attendance = [];
    let downloadedCount = 0;

    if (exportType === 'attendance' || exportType === 'all') {
      let attQuery = { userId };
      if (startDate && endDate) attQuery.date = { $gte: startDate, $lte: endDate };

      attendance = await Attendance.find(attQuery)
        .sort({ date: -1, timestamp: -1 })
        .limit(500);
      console.log(`Found ${attendance.length} attendance records`);

      if (attendance.length > 0) {
        let csvContent = 'Date,Check-in Time,Latitude,Longitude,Address,Selfie Filename\n';
        const selfiesDir = path.join(userTempDir, 'selfies');
        fs.mkdirSync(selfiesDir, { recursive: true });

        for (let i = 0; i < Math.min(attendance.length, 100); i++) {
          const att = attendance[i];
          const fname = `selfie_${getISTDate(att)}_${getISTTime(att).replace(/:/g, '-')}.jpg`;
          const fpath = path.join(selfiesDir, fname);
          csvContent += `"${getISTDate(att)}","${getISTTime(att)}",${att.latitude},${att.longitude},"${att.address || 'Address unavailable'}","selfies/${fname}"\n`;
          if (att.selfiePath) {
            const ok = await downloadImage(att.selfiePath, fpath);
            if (ok) downloadedCount++;
          }
        }
        for (let i = 100; i < attendance.length; i++) {
          const att = attendance[i];
          csvContent += `"${getISTDate(att)}","${getISTTime(att)}",${att.latitude},${att.longitude},"${att.address || 'Address unavailable'}","${att.selfiePath}"\n`;
        }

        const csvPath = path.join(userTempDir, `${user.employeeId}_attendance.csv`);
        fs.writeFileSync(csvPath, csvContent);
        console.log(`Attendance CSV written: ${attendance.length} rows, ${downloadedCount} selfies`);
      }
    }

    // ── EXPENSES: PDF (TOUR_FORM layout) + receipt images ────────────
    const Trip = require('../models/Trip');
    let trips = [];

    if (exportType === 'expenses' || exportType === 'all') {
      let tripQuery = { userId };
      if (startDate && endDate) {
        tripQuery.createdAt = {
          $gte: new Date(startDate),
          $lte: new Date(new Date(endDate).setHours(23, 59, 59))
        };
      }
      trips = await Trip.find(tripQuery).sort({ createdAt: -1 });
      console.log(`Found ${trips.length} expense trips`);

      if (trips.length > 0) {
        // Generate expense PDF
        const PDFDocument = require('pdfkit');
        const expPdfPath = path.join(userTempDir, `${user.employeeId}_expenses.pdf`);
        const expDoc = new PDFDocument({ margin: 40, size: 'A4', bufferPages: true });
        const expStream = fs.createWriteStream(expPdfPath);
        expDoc.pipe(expStream);
        trips.forEach((trip, i) => generateTripPdf(expDoc, trip, i === 0));
        expDoc.end();
        await new Promise((resolve, reject) => {
          expStream.on('finish', resolve);
          expStream.on('error', reject);
        });
        console.log(`Expense PDF written: ${trips.length} trips`);

        // Download receipt images
        const receiptsDir = path.join(userTempDir, 'receipts');
        fs.mkdirSync(receiptsDir, { recursive: true });
        let rcIdx = 0;
        for (const trip of trips) {
          for (const exp of trip.expenses) {
            if (exp.receiptUrl) {
              const ext = exp.receiptUrl.toLowerCase().includes('.pdf') ? 'pdf' : 'jpg';
              const rcPath = path.join(receiptsDir, `receipt_${trip.employeeId}_${rcIdx++}.${ext}`);
              await downloadReceiptToTemp(exp.receiptUrl, rcPath);
            }
          }
        }
        console.log(`Downloaded ${rcIdx} receipts`);
      }
    }

    // Nothing to export?
    if (attendance.length === 0 && trips.length === 0) {
      fs.rmSync(userTempDir, { recursive: true, force: true });
      return res.status(404).json({
        success: false,
        message: 'No records found for the selected date range and export type'
      });
    }

    // ── user_info.json ────────────────────────────────────────────────
    fs.writeFileSync(path.join(userTempDir, 'user_info.json'), JSON.stringify({
      employeeId: user.employeeId,
      name: user.name,
      email: user.email,
      department: user.department,
      designation: user.designation,
      dateRange: { startDate: startDate || 'All', endDate: endDate || 'All' },
      exportType,
      attendanceRecords: attendance.length,
      downloadedSelfies: downloadedCount,
      expenseTrips: trips.length,
      exportDate: new Date().toISOString()
    }, null, 2));

    // ── Build ZIP ─────────────────────────────────────────────────────
    const archive = archiver('zip', { zlib: { level: 6 }, statConcurrency: 1 });
    const zipFilename = `${user.employeeId}_${exportType}_export_${Date.now()}.zip`;

    res.setHeader('Content-Type', 'application/zip');
    res.setHeader('Content-Disposition', `attachment; filename="${zipFilename}"`);
    res.setHeader('Cache-Control', 'no-cache');
    res.setHeader('Content-Transfer-Encoding', 'binary');

    archive.on('error', (err) => {
      console.error('Archive error:', err);
      try { fs.rmSync(userTempDir, { recursive: true, force: true }); } catch (e) {}
      if (!res.headersSent) res.status(500).json({ success: false, message: err.message });
      else res.end();
    });
    archive.on('end', () => console.log('Archive flushed'));
    archive.pipe(res);

    const rootFolder = `${user.employeeId}_${exportType}_${moment().format('YYYY-MM-DD')}`;

    // Walk and add all files from temp dir
    const walkAndAdd = (dir, archiveBase) => {
      for (const entry of fs.readdirSync(dir)) {
        const full = path.join(dir, entry);
        if (fs.statSync(full).isDirectory()) {
          walkAndAdd(full, `${archiveBase}/${entry}`);
        } else {
          archive.file(full, { name: `${archiveBase}/${entry}` });
        }
      }
    };
    walkAndAdd(userTempDir, rootFolder);

    await archive.finalize();
    console.log(`✅ Export ZIP done: ${zipFilename}, ${archive.pointer()} bytes`);

    await new Promise((resolve) => {
      res.on('finish', resolve);
      res.on('error', resolve);
    });

    setTimeout(() => {
      try { fs.rmSync(userTempDir, { recursive: true, force: true }); } catch (e) {}
    }, 3000);

  } catch (error) {
    console.error('❌ exportUserData error:', error);
    try { fs.rmSync(userTempDir, { recursive: true, force: true }); } catch (e) {}
    if (!res.headersSent) {
      res.status(500).json({ success: false, message: error.message || 'Export failed' });
    }
  }
};

// @desc    Export all users data as ZIP  (attendance | expenses | all)
// @route   POST /api/admin/export/all
// @access  Private/Admin
exports.exportAllData = async (req, res) => {
  const tempDir = path.join(__dirname, '../../temp');
  const exportTempDir = path.join(tempDir, `all_export_${Date.now()}`);

  try {
    const { startDate, endDate, exportType = 'all' } = req.body;
    // exportType: 'attendance' | 'expenses' | 'all'
    console.log('Export all request:', { startDate, endDate, exportType });

    req.setTimeout(0);
    res.setTimeout(0);

    if (!fs.existsSync(tempDir)) fs.mkdirSync(tempDir, { recursive: true });
    fs.mkdirSync(exportTempDir, { recursive: true });

    // ── ATTENDANCE CSV ────────────────────────────────────────────────
    let attendance = [];
    if (exportType === 'attendance' || exportType === 'all') {
      let attQ = {};
      if (startDate && endDate) attQ.date = { $gte: startDate, $lte: endDate };
      attendance = await Attendance.find(attQ)
        .populate('userId', 'name employeeId email department designation')
        .sort({ date: -1, timestamp: -1 })
        .limit(1000);
      console.log(`Found ${attendance.length} attendance records`);

      if (attendance.length > 0) {
        let csvContent = 'Employee ID,Employee Name,Department,Designation,Date,Check-in Time,Latitude,Longitude,Address,Selfie URL\n';
        attendance.forEach(att => {
          csvContent += `"${att.employeeId}","${att.userId ? att.userId.name : 'Unknown'}",`;
          csvContent += `"${att.userId ? att.userId.department : ''}","${att.userId ? att.userId.designation : ''}",`;
          csvContent += `"${getISTDate(att)}","${getISTTime(att)}",`;
          csvContent += `${att.latitude},${att.longitude},`;
          csvContent += `"${att.address || 'Address unavailable'}","${att.selfiePath}"\n`;
        });
        fs.writeFileSync(path.join(exportTempDir, 'all_attendance.csv'), csvContent);
        console.log(`Attendance CSV written: ${attendance.length} rows`);
      }
    }

    // ── EXPENSE PDF (TOUR_FORM layout, one page per trip) ─────────────
    const Trip = require('../models/Trip');
    let trips = [];
    if (exportType === 'expenses' || exportType === 'all') {
      let tripQ = {};
      if (startDate && endDate) {
        tripQ.createdAt = {
          $gte: new Date(startDate),
          $lte: new Date(new Date(endDate).setHours(23, 59, 59))
        };
      }
      trips = await Trip.find(tripQ).sort({ employeeId: 1, createdAt: -1 });
      console.log(`Found ${trips.length} expense trips`);

      if (trips.length > 0) {
        const PDFDocument = require('pdfkit');
        const expPdfPath = path.join(exportTempDir, 'all_expenses.pdf');
        const expDoc = new PDFDocument({ margin: 40, size: 'A4', bufferPages: true });
        const expStream = fs.createWriteStream(expPdfPath);
        expDoc.pipe(expStream);
        trips.forEach((trip, i) => generateTripPdf(expDoc, trip, i === 0));
        expDoc.end();
        await new Promise((resolve, reject) => {
          expStream.on('finish', resolve);
          expStream.on('error', reject);
        });
        console.log(`All-expenses PDF written: ${trips.length} trips`);
      }
    }

    if (attendance.length === 0 && trips.length === 0) {
      fs.rmSync(exportTempDir, { recursive: true, force: true });
      return res.status(404).json({
        success: false,
        message: 'No records found for the selected date range and export type'
      });
    }

    // ── summary.json ──────────────────────────────────────────────────
    fs.writeFileSync(path.join(exportTempDir, 'summary.json'), JSON.stringify({
      dateRange: { startDate: startDate || 'All', endDate: endDate || 'All' },
      exportType,
      attendanceRecords: attendance.length,
      expenseTrips: trips.length,
      exportDate: new Date().toISOString()
    }, null, 2));

    // ── Build ZIP ─────────────────────────────────────────────────────
    const archive = archiver('zip', { zlib: { level: 6 }, statConcurrency: 1 });
    const zipFilename = `all_${exportType}_export_${Date.now()}.zip`;

    res.setHeader('Content-Type', 'application/zip');
    res.setHeader('Content-Disposition', `attachment; filename="${zipFilename}"`);
    res.setHeader('Cache-Control', 'no-cache');
    res.setHeader('Content-Transfer-Encoding', 'binary');

    archive.on('error', (err) => {
      console.error('Archive error:', err);
      try { fs.rmSync(exportTempDir, { recursive: true, force: true }); } catch (e) {}
      if (!res.headersSent) res.status(500).json({ success: false, message: err.message });
      else res.end();
    });
    archive.on('end', () => console.log('Archive flushed'));
    archive.pipe(res);

    const rootFolder = `all_${exportType}_${moment().format('YYYY-MM-DD')}`;
    for (const file of fs.readdirSync(exportTempDir)) {
      archive.file(path.join(exportTempDir, file), { name: `${rootFolder}/${file}` });
    }

    await archive.finalize();
    console.log(`✅ Export all ZIP done: ${zipFilename}, ${archive.pointer()} bytes`);

    await new Promise((resolve) => {
      res.on('finish', resolve);
      res.on('error', resolve);
    });

    setTimeout(() => {
      try { fs.rmSync(exportTempDir, { recursive: true, force: true }); } catch (e) {}
    }, 3000);

  } catch (error) {
    console.error('❌ exportAllData error:', error);
    try { fs.rmSync(exportTempDir, { recursive: true, force: true }); } catch (e) {}
    if (!res.headersSent) {
      res.status(500).json({ success: false, message: error.message || 'Export failed' });
    }
  }
};

// @desc    Export attendance as CSV
// @route   GET /api/admin/export/attendance/csv
// @access  Private/Admin
exports.exportAttendanceCSV = async (req, res) => {
  try {
    const { startDate, endDate } = req.query;

    console.log('CSV Export:', { startDate, endDate });

    let query = {};
    if (startDate && endDate) {
      query.date = { $gte: startDate, $lte: endDate };
    }

    const attendance = await Attendance.find(query)
      .populate('userId', 'name employeeId department designation')
      .sort({ date: -1, timestamp: -1 })
      .limit(5000);

    console.log(`Exporting ${attendance.length} records as CSV`);

    // ✅ Added Address column
    let csv = 'Employee ID,Employee Name,Department,Designation,Date,Check-in Time,Latitude,Longitude,Address,Selfie URL\n';
    
    attendance.forEach(att => {
      csv += `"${att.employeeId}",`;
      csv += `"${att.userId ? att.userId.name : 'Unknown'}",`;
      csv += `"${att.userId ? att.userId.department : ''}",`;
      csv += `"${att.userId ? att.userId.designation : ''}",`;
      csv += `"${getISTDate(att)}",`;
      csv += `"${getISTTime(att)}",`;
      csv += `${att.latitude},`;
      csv += `${att.longitude},`;
      csv += `"${att.address || 'Address unavailable'}",`; // ✅ Added address field
      csv += `"${att.selfiePath}"\n`;
    });

    const filename = `attendance_${startDate || 'all'}_to_${endDate || 'all'}_${Date.now()}.csv`;
    res.setHeader('Content-Type', 'text/csv; charset=utf-8');
    res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
    res.status(200).send(csv);

    console.log(`✅ CSV exported: ${filename}`);
  } catch (error) {
    console.error('Export CSV error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};

// @desc    Export attendance as PDF
// @route   GET /api/admin/export/attendance/pdf
// @access  Private/Admin
exports.exportAttendancePDF = async (req, res) => {
  try {
    const { startDate, endDate } = req.query;

    console.log('PDF Export:', { startDate, endDate });

    let query = {};
    if (startDate && endDate) {
      query.date = { $gte: startDate, $lte: endDate };
    }

    const attendance = await Attendance.find(query)
      .populate('userId', 'name employeeId department designation')
      .sort({ date: -1, timestamp: -1 })
      .limit(1000);

    console.log(`Exporting ${attendance.length} records as PDF`);

    // ✅ Use landscape orientation for wider table with address
    const doc = new PDFDocument({ 
      margin: 30, 
      size: 'A4',
      layout: 'landscape', // ✅ Changed to landscape for address column
      bufferPages: true
    });
    
    const filename = `attendance_${startDate || 'all'}_to_${endDate || 'all'}_${Date.now()}.pdf`;

    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);

    doc.pipe(res);

    doc.fontSize(20).text('Attendance Report', { align: 'center' });
    doc.moveDown();
    
    doc.fontSize(12).text(`Date Range: ${startDate || 'All'} to ${endDate || 'All'}`, { align: 'center' });
    doc.fontSize(12).text(`Total Records: ${attendance.length}`, { align: 'center' });
    doc.fontSize(10).text(`Generated: ${new Date().toLocaleString()}`, { align: 'center' });
    doc.moveDown(2);

    const rowHeight = 30; // Increased for address wrapping
    let currentY = doc.y;

    // ✅ Updated column headers with address
    doc.fontSize(8).font('Helvetica-Bold');
    doc.text('Emp ID', 30, currentY, { width: 50 });
    doc.text('Name', 85, currentY, { width: 80 });
    doc.text('Date', 170, currentY, { width: 60 });
    doc.text('Time', 235, currentY, { width: 45 });
    doc.text('Location', 285, currentY, { width: 80 });
    doc.text('Address', 370, currentY, { width: 400 }); // ✅ Added address column
    
    currentY += rowHeight;
    doc.moveTo(30, currentY - 5).lineTo(780, currentY - 5).stroke();

    doc.font('Helvetica');
    attendance.forEach((att, index) => {
      if (currentY > 520) { // Adjusted for landscape
        doc.addPage();
        currentY = 50;
        
        doc.font('Helvetica-Bold').fontSize(8);
        doc.text('Emp ID', 30, currentY, { width: 50 });
        doc.text('Name', 85, currentY, { width: 80 });
        doc.text('Date', 170, currentY, { width: 60 });
        doc.text('Time', 235, currentY, { width: 45 });
        doc.text('Location', 285, currentY, { width: 80 });
        doc.text('Address', 370, currentY, { width: 400 });
        currentY += rowHeight;
        doc.moveTo(30, currentY - 5).lineTo(780, currentY - 5).stroke();
        doc.font('Helvetica');
      }

      doc.fontSize(7);
      doc.text(att.employeeId || '', 30, currentY, { width: 50 });
      doc.text(att.userId ? att.userId.name : 'Unknown', 85, currentY, { width: 80 });
      doc.text(getISTDate(att), 170, currentY, { width: 60 });
      doc.text(getISTTime(att), 235, currentY, { width: 45 });
      doc.text(`${att.latitude.toFixed(4)}, ${att.longitude.toFixed(4)}`, 285, currentY, { width: 80 });
      // ✅ Added address with word wrapping
      doc.text(att.address || 'Address unavailable', 370, currentY, { 
        width: 400,
        height: rowHeight - 5,
        ellipsis: true
      });
      
      currentY += rowHeight;
      
      if (index < attendance.length - 1) {
        doc.moveTo(30, currentY - 2).lineTo(780, currentY - 2).stroke('#CCCCCC');
      }
    });

    doc.end();

    console.log(`✅ PDF exported: ${filename}`);

  } catch (error) {
    console.error('Export PDF error:', error);
    if (!res.headersSent) {
      res.status(500).json({
        success: false,
        message: error.message
      });
    }
  }
};

// @desc    Export attendance as JSON
// @route   GET /api/admin/export/attendance/json
// @access  Private/Admin
exports.exportAttendanceJSON = async (req, res) => {
  try {
    const { startDate, endDate } = req.query;

    let query = {};
    if (startDate && endDate) {
      query.date = { $gte: startDate, $lte: endDate };
    }

    const attendance = await Attendance.find(query)
      .populate('userId', 'name employeeId department designation')
      .sort({ date: -1, timestamp: -1 })
      .limit(5000);

    const exportData = {
      exportDate: new Date().toISOString(),
      dateRange: {
        startDate: startDate || 'All',
        endDate: endDate || 'All'
      },
      totalRecords: attendance.length,
      attendance: attendance.map(att => ({
        employeeId: att.employeeId,
        employeeName: att.userId ? att.userId.name : 'Unknown',
        department: att.userId ? att.userId.department : '',
        designation: att.userId ? att.userId.designation : '',
        date: getISTDate(att),
        checkInTime: getISTTime(att),
        latitude: att.latitude,
        longitude: att.longitude,
        address: att.address || 'Address unavailable', // ✅ Added address field
        selfieUrl: att.selfiePath,
        timestamp: att.timestamp
      }))
    };

    const filename = `attendance_${Date.now()}.json`;
    res.setHeader('Content-Type', 'application/json');
    res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
    res.status(200).json(exportData);
  } catch (error) {
    console.error('Export JSON error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};