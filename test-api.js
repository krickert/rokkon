#!/usr/bin/env node

const http = require('http');

// Test ping endpoint
function testPing() {
    return new Promise((resolve, reject) => {
        http.get('http://localhost:38090/ping', (res) => {
            let data = '';
            res.on('data', (chunk) => { data += chunk; });
            res.on('end', () => {
                console.log('Ping response:', res.statusCode, data);
                resolve(data);
            });
        }).on('error', (err) => {
            console.error('Ping error:', err.message);
            reject(err);
        });
    });
}

// Test consul status endpoint
function testConsulStatus() {
    return new Promise((resolve, reject) => {
        http.get('http://localhost:38090/api/consul/status', (res) => {
            let data = '';
            res.on('data', (chunk) => { data += chunk; });
            res.on('end', () => {
                console.log('Consul status response:', res.statusCode, data);
                try {
                    const json = JSON.parse(data);
                    console.log('Parsed:', json);
                    resolve(json);
                } catch (e) {
                    console.error('Failed to parse JSON:', e);
                    reject(e);
                }
            });
        }).on('error', (err) => {
            console.error('Consul status error:', err.message);
            reject(err);
        });
    });
}

// Run tests
async function runTests() {
    console.log('Testing Rokkon Engine endpoints...\n');
    
    try {
        console.log('1. Testing /ping endpoint:');
        await testPing();
        
        console.log('\n2. Testing /api/consul/status endpoint:');
        await testConsulStatus();
        
        console.log('\nAll tests completed successfully!');
    } catch (error) {
        console.error('\nTest failed:', error);
        process.exit(1);
    }
}

runTests();